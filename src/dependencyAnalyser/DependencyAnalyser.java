package dependencyAnalyser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DependencyAnalyser {
    private static AppStatus appStatus = new AppStatus(Status.OK, "OK");

    private static String rootPath;

    private static SortMode sortMode;

    private final static List<File> files = new ArrayList<>();

    private final static Map<File, List<File>> fileDependencies = new HashMap<>();

    public DependencyAnalyser() {
        configureApp();
    }

    enum Status {
        OK, FOUND_CYCLE, INVALID_REQUIREMENT, NO_FILES, READING_ERROR
    }

    enum SortMode {
        PATH, TOPOLOGY
    }

    private record AppStatus(Status status, String message) {
        @Override
        public String toString() {
            return "Status: " + status.toString() + " Message: " + message;
        }
    }

    private void configureApp() {
        System.out.print("Enter root location: ");
        Scanner scanner = new Scanner(System.in);
        rootPath = scanner.nextLine();
        if (rootPath.charAt(rootPath.length() - 1) != File.separatorChar) {
            rootPath += File.separator;
        }
        setSortType();
    }

    private void setSortType() {
        System.out.println("Choose type of sort: \n1. Path\n2. Topology");
        boolean isSet = false;
        Scanner scanner = new Scanner(System.in);
        int ans;
        while (!isSet) {
            ans = scanner.nextInt();
            switch (ans) {
                case 1 -> {
                    sortMode = SortMode.PATH;
                    isSet = true;
                }
                case 2 -> {
                    sortMode = SortMode.TOPOLOGY;
                    isSet = true;
                }
                default -> System.out.println("Invalid value. Repeat operation.");
            }
        }
    }

    public void run() {
        getDirectoryContent(rootPath);
        if (appStatus.status == Status.OK && sortMode == SortMode.TOPOLOGY) {
            GraphInterpreter.buildGraph();
        }
        if (appStatus.status == Status.OK) {
            sortFiles();
        }
        if (appStatus.status == Status.OK) {
            saveSortedFiles();
        }
        finishApp();
    }

    private void finishApp() {
        if (appStatus.status == Status.OK) {
            printSortedFiles();
        } else {
            System.out.println(appStatus);
        }
    }

    /**
     * Shows files location relative to the root.
     */
    private void printSortedFiles() {
        if (sortMode == SortMode.TOPOLOGY) {
            System.out.println("Sorted by TOPOLOGY files: ");
            for (int i = 0; i < files.size(); i++) {
                System.out.println(getPathFromRoot(files.get(GraphInterpreter.sortedVerticals.get(i)).getAbsolutePath()));
            }
        } else if (sortMode == SortMode.PATH) {
            System.out.println("Sorted by PATH files: ");
            for (var file : files) {
                System.out.println(getPathFromRoot(file.getAbsolutePath()));
            }
        }
    }

    private void getDirectoryContent(String directoryPath) {
        File root = new File(directoryPath);
        String resultFilename = "sorted.txt";
        File resultFile = new File(rootPath + resultFilename);
        if (root.isDirectory()) {
            for (File item : Objects.requireNonNull(root.listFiles())) {
                if (item.isDirectory()) {
                    getDirectoryContent(item.getAbsolutePath());
                } else if (!resultFile.equals(item)) {
                    getFileContent(item);
                }
            }
        }
        if (files.size() == 0) {
            appStatus = new AppStatus(Status.NO_FILES, "No files were found");
        }
    }

    private void getFileContent(File file) {
        files.add(file);

        try (FileReader fr = new FileReader(file)) {
            BufferedReader bf = new BufferedReader(fr);
            String line = bf.readLine();
            while (line != null) {
                String[] words = line.split(" ");
                String commandPath = "'.*'";
                Pattern pathPattern = Pattern.compile(commandPath);
                if (words[0].equals("require")) {
                    var dependencies = fileDependencies.get(file);

                    if (dependencies == null) {
                        dependencies = new ArrayList<>();
                    }

                    Matcher matcher = pathPattern.matcher(line);
                    while (matcher.find()) {
                        var res = matcher.group();
                        var filePath = rootPath + res.substring(1, res.length() - 1);

                        File tmp_file = new File(filePath);
                        if (!tmp_file.exists()) {
                            appStatus = new AppStatus(Status.INVALID_REQUIREMENT, "Invalid requirement in file: " + getPathFromRoot(filePath));
                            return;
                        }
                        dependencies.add(tmp_file);
                    }
                    fileDependencies.put(file, dependencies);
                }
                line = bf.readLine();
            }
        } catch (IOException e) {
            appStatus = new AppStatus(Status.READING_ERROR, e.getMessage());
        }
    }

    private static String getPathFromRoot(String absolutePath) {
        if (absolutePath != null) {
            return absolutePath.substring(rootPath.length());
        }
        return "";
    }

    private void saveSortedFiles() {
        try (FileWriter writer = new FileWriter(rootPath + File.separatorChar + "sorted.txt")) {
            for (var file : files) {
                writer.write(Files.readString(Path.of(file.getAbsolutePath())));
                writer.write("\n");
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private void sortFiles() {
        if (sortMode == SortMode.TOPOLOGY) {
            GraphInterpreter.sortGraph();
        } else if (sortMode == SortMode.PATH) {
            Collections.sort(files);
        }
    }

    public static class GraphInterpreter {
        private final static Stack<Integer> sortedVerticals = new Stack<>();

        private static int[][] dependencyMatrix;

        private static boolean[] usedVerticals;

        public static void buildGraph() {
            dependencyMatrix = new int[files.size()][files.size()];
            for (int i = 0; i < files.size(); i++) {
                for (int j = 0; j < files.size(); j++) {
                    if (fileDependencies.containsKey(files.get(i))) {
                        var depends = fileDependencies.get(files.get(i));
                        if (depends.contains(files.get(j))) {
                            dependencyMatrix[i][j] = 1;
                        }
                        if (dependencyMatrix[i][j] == 1 && dependencyMatrix[j][i] == 1) {
                            appStatus = new AppStatus(Status.FOUND_CYCLE, "Found cycle: " +
                                    getPathFromRoot(files.get(i).getAbsolutePath()) + " " +
                                    getPathFromRoot(files.get(j).getAbsolutePath()));
                            return;
                        }
                    }
                }
            }
        }

        private static void sortGraph() {
            if (sortMode == SortMode.TOPOLOGY) {
                usedVerticals = new boolean[files.size()];
                for (int i = 0; i < files.size(); i++) {
                    if (appStatus.status == Status.FOUND_CYCLE) {
                        break;
                    }
                    if (!usedVerticals[i]) {
                        dfs(-1, -1, i);
                    }
                }
            }
        }

        private static void dfs(int startIndex, int prevIndex, int index) {
            if (startIndex == index) {
                appStatus = new AppStatus(Status.FOUND_CYCLE, "Found cycle: " +
                        getPathFromRoot(files.get(prevIndex).getAbsolutePath()) + " " +
                        getPathFromRoot(files.get(startIndex).getAbsolutePath()));
                return;
            }

            if (startIndex == -1) {
                startIndex = 0;
            }

            for (int j = 0; j < files.size(); j++) {
                if (dependencyMatrix[index][j] == 1 && !usedVerticals[j]) {
                    dfs(startIndex, index, j);
                }
            }

            if (!usedVerticals[index]) {
                usedVerticals[index] = true;
                sortedVerticals.push(index);
            }
        }
    }
}