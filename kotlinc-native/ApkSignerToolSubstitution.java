import com.oracle.svm.core.annotate.Substitute;
import com.oracle.svm.core.annotate.TargetClass;

@TargetClass(className = "com.android.apksigner.ApkSignerTool")
final class ApkSignerToolSubstitution {
    @Substitute
    private static void addProviders() {
    }
}
