package utils;

import java.io.File;

public class PathUtil {
    public static String getPathFromRoot(String rootPath, File file) {
        if (file.exists()) {
            return file.getAbsolutePath().substring(rootPath.length());
        }
        return "";
    }
}
