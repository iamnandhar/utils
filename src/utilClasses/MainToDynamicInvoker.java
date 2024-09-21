import java.util.List;

public class Application {
    public static void main(String[] args) {
        try {
            // Load the methods from the configuration file
            List<String> methods = MethodLoader.loadMethods("methods.txt");

            // Create the DTO instance
            MyDTO dto = new MyDTO("Sample Data");

            // Create an instance of DynamicInvoker
            DynamicInvoker invoker = new DynamicInvoker();

            // Invoke the methods
            invoker.invokeMethods(methods, dto);

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
