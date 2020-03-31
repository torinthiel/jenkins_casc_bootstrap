package pl.torinthiel.jenkins.bootstrap;

import java.io.ByteArrayOutputStream;
import java.io.IOException;

import org.testcontainers.containers.output.BaseConsumer;
import org.testcontainers.containers.output.OutputFrame;

import com.google.common.base.Charsets;


// An almost exact copy of org.testcontainers.containers.output.ToStringConsumer
// Differences:
// a) Only UTF8 output
// b) Doesn't exhibit the original behaviour of adding a newline every frame
class ToStringConsumer extends BaseConsumer<ToStringConsumer> {
    private ByteArrayOutputStream stringBuffer = new ByteArrayOutputStream();

    @Override
    public void accept(OutputFrame outputFrame) {
        try {
            if (outputFrame.getBytes() != null) {
                stringBuffer.write(outputFrame.getBytes());
                stringBuffer.flush();
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public String toUtf8String() {
        byte[] bytes = stringBuffer.toByteArray();
        return new String(bytes, Charsets.UTF_8);
    }
}
