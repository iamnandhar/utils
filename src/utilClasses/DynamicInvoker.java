package src.utilClasses;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;

public class DynamicInvoker {
    private static final Logger logger = LoggerFactory.getLogger(DynamicInvoker.class);
    private static final List<String> ALLOWED_PACKAGES = Arrays.asList("com.example.utils");

    public void invokeMethods(List<String> methodNames, Object dto) {
        for (String fullMethodName : methodNames) {
            try {
                logger.info("Entering method: {}", fullMethodName);

                int lastDotIndex = fullMethodName.lastIndexOf('.');
                if (lastDotIndex == -1) {
                    logger.error("Invalid method name: {}", fullMethodName);
                    continue;
                }
                String className = fullMethodName.substring(0, lastDotIndex);
                String methodName = fullMethodName.substring(lastDotIndex + 1);

                Class<?> clazz = Class.forName(className);

                // Security check: Ensure class is in allowed packages
                Package classPackage = clazz.getPackage();
                if (!ALLOWED_PACKAGES.contains(classPackage.getName())) {
                    logger.error("Access to class {} is not allowed", className);
                    continue;
                }

                Method method = findMethod(clazz, methodName, dto.getClass());
                if (method == null) {
                    logger.error("Method {} not found in class {}", methodName, className);
                    continue;
                }

                // Invoke the method
                Object result = method.invoke(null, dto);

                logger.info("Exiting method: {}", fullMethodName);
            } catch (Exception e) {
                logger.error("Exception in method: {}", fullMethodName, e);
            }
        }
    }

    private Method findMethod(Class<?> clazz, String methodName, Class<?> dtoClass) {
        Class<?> currentClass = dtoClass;
        while (currentClass != null) {
            try {
                return clazz.getMethod(methodName, currentClass);
            } catch (NoSuchMethodException e) {
                currentClass = currentClass.getSuperclass();
            }
        }
        for (Class<?> iface : dtoClass.getInterfaces()) {
            try {
                return clazz.getMethod(methodName, iface);
            } catch (NoSuchMethodException e) {
                // Continue to next interface
            }
        }
        return null;
    }
}

