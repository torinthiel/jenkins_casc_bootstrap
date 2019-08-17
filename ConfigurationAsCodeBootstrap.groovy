import static java.util.logging.Level.INFO
import static java.util.logging.Level.WARNING;
import java.util.logging.Logger;

println "A message from println"

Logger.getLogger(getClass().name).log(WARNING, "A warninig via logger")
