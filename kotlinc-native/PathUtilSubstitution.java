import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;
import java.io.File;

@TargetClass(className = "org.jetbrains.kotlin.utils.PathUtil")
final class PathUtilSubstitution {
    @Substitute
    public static File getResourcePathForClass(Class<?> javaClass) {
        String home = System.getProperty("kotlin.home");
        if (home == null) {
            home = System.getenv("KOTLIN_HOME");
        }
        if (home == null) {
            throw new IllegalStateException(
                "Set -Dkotlin.home=<kotlinc dist dir> for native kotlinc");
        }
        return new File(new File(home, "lib"), "kotlin-compiler.jar");
    }
}
