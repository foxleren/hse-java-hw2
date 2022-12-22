package dependencyAnalyser;

import java.io.*;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DependencyAnalyser {
    private AppStatus appStatus = new AppStatus(Status.OK, "OK");

    private String rootPath;

    private SortMode sortMode;

    private final List<String> files = new ArrayList<>();

    private final Map<String, List<String>> fileDependencies = new HashMap<>();

    private final Stack<Integer> sortedVerticals = new Stack<>();

    private int[][] dependencyMatrix;

    private boolean[] usedVerticals;

    public DependencyAnalyser() {
        configureApp();
    }

    enum Status {
        OK,
        FOUND_CYCLE,
        INVALID_REQUIREMENT,
        NO_FILES,
        READING_ERROR
    }

    enum SortMode {
        NAMING,
        TOPOLOGY
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
        if (rootPath.charAt(rootPath.length() - 1) != '/') {
            rootPath += "/";
        }
        setSortType();
    }

    private void setSortType() {
        System.out.println("Choose type of sort: \n1. Naming\n2. Topology");
        boolean isSet = false;
        Scanner scanner = new Scanner(System.in);
        int ans;
        while (!isSet) {
            ans = scanner.nextInt();
            switch (ans) {
                case 1 -> {
                    sortMode = SortMode.NAMING;
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
        if (appStatus.status == Status.OK) {
            setDependencyMatrix();
        }
        if (appStatus.status == Status.OK) {
            sortFiles();
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
                System.out.println(getPathFromRoot(files.get(sortedVerticals.get(i))));
            }
        } else if (sortMode == SortMode.NAMING) {
            System.out.println("Sorted by NAMING files: ");
            for (int i = 0; i < files.size(); i++) {
                System.out.println(getPathFromRoot(files.get(i)));
            }
        }
    }

    private void getDirectoryContent(String directoryPath) {
        File root = new File(directoryPath);
        if (root.isDirectory()) {
            for (File item : Objects.requireNonNull(root.listFiles())) {
                if (item.isDirectory()) {
                    getDirectoryContent(item.getAbsolutePath());
                } else {
                    getFileContent(item);
                }
            }
        }
        if (files.size() == 0) {
            appStatus = new AppStatus(Status.NO_FILES, "No files were found");
        }
    }

    private void getFileContent(File file) {
        files.add(file.getAbsolutePath());

        try (FileReader fr = new FileReader(file)) {
            BufferedReader bf = new BufferedReader(fr);
            String line = bf.readLine();
            while (line != null) {
                String[] words = line.split(" ");
                String commandPath = "'.*'";
                Pattern pathPattern = Pattern.compile(commandPath);
                if (words[0].equals("require")) {
                    var dependencies = fileDependencies.get(file.getAbsolutePath());

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
                        dependencies.add(filePath);
                    }
                    fileDependencies.put(file.getAbsolutePath(), dependencies);
                }
                line = bf.readLine();
            }
        } catch (IOException e) {
            appStatus = new AppStatus(Status.READING_ERROR, e.getMessage());
        }
    }

    private void setDependencyMatrix() {
        dependencyMatrix = new int[files.size()][files.size()];

        for (int i = 0; i < files.size(); i++) {
            for (int j = 0; j < files.size(); j++) {
                if (fileDependencies.containsKey(files.get(i))) {
                    var depends = fileDependencies.get(files.get(i));
                    if (depends.contains(files.get(j))) {
                        dependencyMatrix[i][j] = 1;
                    }
                    if (dependencyMatrix[i][j] == 1 && dependencyMatrix[j][i] == 1) {
                        appStatus = new AppStatus(Status.FOUND_CYCLE, "Found cycle in file: " + getPathFromRoot(files.get(i)));
                        return;
                    }
                }
            }
        }
    }

    private void sortFiles() {
        if (sortMode == SortMode.TOPOLOGY) {
            usedVerticals = new boolean[files.size()];
            for (int i = 0; i < files.size(); i++) {
                if (!usedVerticals[i]) {
                    dfs(i);
                }
            }
        } else if (sortMode == SortMode.NAMING) {
            Collections.sort(files);
        }
    }

    private void dfs(int index) {
        for (int j = 0; j < files.size(); j++) {
            if (dependencyMatrix[index][j] == 1 && !usedVerticals[j]) {
                dfs(j);
            }
        }
        if (!usedVerticals[index]) {
            usedVerticals[index] = true;
            sortedVerticals.push(index);
        }
    }

    private String getPathFromRoot(String absolutePath) {
        if (absolutePath != null) {
            return absolutePath.substring(rootPath.length());
        }
        return "";
    }
}