package pl.torinthiel.jenkins.bootstrap;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;

import org.testcontainers.containers.Container;
import org.testcontainers.images.builder.Transferable;
import org.testcontainers.vault.VaultContainer;

import com.github.dockerjava.api.command.InspectContainerResponse;

public class ExtendedVaultContainer<SELF extends ExtendedVaultContainer<SELF>> extends VaultContainer<SELF> {
	List<VaultCommand> commands = new ArrayList<>();
	boolean initialized = false;

	public ExtendedVaultContainer() {
		super();
	}

	public ExtendedVaultContainer(String image) {
		super(image);
	}

	@Override
	protected void containerIsStarted(InspectContainerResponse containerInfo) {
		initialized = true;
		commands.forEach(this::apply);

		super.containerIsStarted(containerInfo);
	}

	public SELF withAuthEngine(String name) {
		return withAuthEngine(name, name);
	}

	public SELF withAuthEngine(String name, String path) {
		stageOrApply(new AuthEngineCommand(name, path));
		return self();
	}

	public SELF withKv(String path, String... values) {
		stageOrApply(new KeyValueCommand(path, values));
		return self();
	}

	public SELF withKvAndStdin(String path, Transferable stdin, String... values) {
		stageOrApply(new KeyValueWithStdin(path, stdin, values));
		return self();
	}

	public SELF withPolicy(String policyName, Transferable policyContents) {
		stageOrApply(new PolicyWithStdin(policyName, policyContents));
		return self();
	}

	public SELF withSecretInVault2(String path, String... values) {
		// There are two problems with original .withSecretInVault
		// 1. it requires at least one key-value pair, which is sometimes unncesessary
		// 2. It "orders" writes via HashMap, which causes problems when a write creates endpoint for the next call
		stageOrApply(new WriteCommand(path, values));
		return self();
	}

	private void stageOrApply(VaultCommand command) {
		if (initialized) {
			apply(command);
		} else {
			commands.add(command);
		}
	}

	private void apply(VaultCommand command) {
		logger().info("Running command {}", command);
		try {
			command.preExec(this);
			ExecResult result = execInContainer(command.toCommand());
			logger().info("Command exit code: {}", result.getExitCode());
		} catch (UnsupportedOperationException | IOException | InterruptedException e) {
		    logger().error("Failed to execute {} command inside vault. Exception message: {}", command, e.getMessage());
		}
	}

	abstract class VaultCommand {
		protected static final String VAULT_COMMAND = "/bin/vault";

		public abstract String[] toCommand();

		public String toString() {
			return Arrays.stream(toCommand()).collect(Collectors.joining(" "));
		}

		public void preExec(Container<?> container) {}
	}

	abstract class VaultCommandWithStdin extends VaultCommand {
		private static final String TMP_FILE = "/tmp/command_stdin";
		private static final String COMMAND_PREFIX = "/bin/cat " + TMP_FILE +  " | " + VAULT_COMMAND + " ";
		private static final String COMMAND_SUFFIX = "; /bin/rm " + TMP_FILE;

		private final String vaultCommand;
		private final Transferable stdin;
		protected final String[] params;

		protected VaultCommandWithStdin(String vaultCommand, Transferable stdin, int paramsLen) {
			super();
			this.vaultCommand = vaultCommand;
			this.stdin = stdin;
			this.params = new String[paramsLen];
		}

		@Override
		public String[] toCommand() {
			StringBuilder buf = new StringBuilder(COMMAND_PREFIX).append(vaultCommand);
			Arrays.asList(params).forEach(v -> buf.append(" '").append(v).append("'"));
			buf.append(COMMAND_SUFFIX);

			return new String[] {"/bin/sh", "-ec", buf.toString()};
		}

		@Override
		public void preExec(Container<?> container) {
			super.preExec(container);
			container.copyFileToContainer(stdin, TMP_FILE);
		}
	}

	class AuthEngineCommand extends VaultCommand {
		private final String name;
		private final String path;

		public AuthEngineCommand(String name, String path) {
			this.name = name;
			this.path = "-path=" + path;
		}

		@Override
		public String[] toCommand() {
			return new String[]{ VAULT_COMMAND, "auth", "enable", path, name };
		}
	}

	class KeyValueCommand extends VaultCommand {
		private final String[] command;

		public KeyValueCommand(String path, String[] values) {
			command = Arrays.copyOf(new String[] {VAULT_COMMAND, "kv", "put", path}, values.length + 4);
			System.arraycopy(values, 0, command, 4, values.length);
		}

		@Override
		public String[] toCommand() {
			return Arrays.copyOf(command, command.length);
		}
	}

	class WriteCommand extends VaultCommand {
		private final String[] command;

		public WriteCommand(String path, String[] values) {
			if (values.length == 0) {
				command = new String[] {VAULT_COMMAND, "write", "-f", path};
			} else {
				command = Arrays.copyOf(new String[] {VAULT_COMMAND, "write", path}, values.length + 3);
				System.arraycopy(values, 0, command, 3, values.length);
			}
		}

		@Override
		public String[] toCommand() {
			return Arrays.copyOf(command, command.length);
		}
}

	class KeyValueWithStdin extends VaultCommandWithStdin {
		public KeyValueWithStdin(String path, Transferable stdin, String[] values) {
			super("kv put", stdin, values.length + 1);
			params[0] = path;
			System.arraycopy(values, 0, params, 1, values.length);
		}
	}

	class PolicyWithStdin extends VaultCommandWithStdin {
		public PolicyWithStdin(String policyName, Transferable policyContents) {
			super("policy write", policyContents, 2);
			params[0] = policyName;
			params[1] = "-";
		}
	}

}
