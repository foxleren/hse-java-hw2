package dependencyAnalyser;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static utils.PathUtil.getPathFromRoot;

/*
Класс, анализирующий зависимости в выбранной директории.
 */
public class DependencyAnalyser {

    /*
    Статус приложения.
     */
    private AppStatus appStatus = new AppStatus(StatusCode.OK, "OK");

    /*
    Путь до корневой директории.
     */
    private String rootPath;

    /*
    Способ сортировки файлов.
     */
    private SortMode sortMode;

    /*
    Список файлов в директории и поддиректориях.
     */
    private final List<File> files = new ArrayList<>();

    /*
    Список файлов и их зависимостей.
     */
    private final Map<File, List<File>> fileDependencies = new HashMap<>();

    /*
    Объект, отвечающий за работу с графом путей.
     */
    private GraphInterpreter graphInterpreter;

    public DependencyAnalyser() {
    }

    /*
    Способ сортировки файлов.
     */
    enum SortMode {
        PATH, TOPOLOGY
    }

    /*
    Конфигурирование.
     */
    private void configureApp() {
        System.out.print("Enter root location: ");
        Scanner scanner = new Scanner(System.in);
        rootPath = scanner.nextLine();
        if (rootPath.charAt(rootPath.length() - 1) != File.separatorChar) {
            rootPath += File.separator;
        }
        setSortType();
    }

    /*
    Выбор способа сортировки.
     */
    private void setSortType() {
        try {
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
        } catch (InputMismatchException e) {
            System.out.println("Invalid value. Repeat operation.");
            setSortType();
        }
    }

    /*
    Метод, управляющий логикой приложения.
     */
    public void run() {
        configureApp();
        getDirectoryContent(rootPath);
        if (appStatus.statusCode() == StatusCode.OK && sortMode == SortMode.TOPOLOGY) {
            graphInterpreter = new GraphInterpreter(rootPath, files, fileDependencies);
            graphInterpreter.buildGraph();
        }

        if (appStatus.statusCode() == StatusCode.OK) {
            sortFiles();
        }
        if (appStatus.statusCode() == StatusCode.OK) {
            saveSortedFiles();
        }
        finishApp();
    }

    /*
    При завершении программы выводятся файлы или инфомация об ошибке, если таковая есть.
     */
    private void finishApp() {
        if (appStatus.statusCode() == StatusCode.OK) {
            printSortedFiles();
        } else {
            System.out.println(appStatus);
        }
    }

    /**
     * Выводит список файлов в зависимости от типа сортировки.
     */
    private void printSortedFiles() {
        if (sortMode == SortMode.TOPOLOGY) {
            System.out.println("Sorted by TOPOLOGY files: ");
            var sortedIndexes = graphInterpreter.getIndexesOfSortedGraph();
            for (Integer sortedIndex : sortedIndexes) {
                System.out.println(getPathFromRoot(rootPath, files.get(sortedIndex)));
            }
        } else if (sortMode == SortMode.PATH) {
            System.out.println("Sorted by PATH files: ");
            for (var file : files) {
                System.out.println(getPathFromRoot(rootPath, file));
            }
        }
    }

    /*
    Рекурсивное получение содержимого директорий.
     */
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
            appStatus = new AppStatus(StatusCode.NO_FILES, "No files were found");
        }
    }

    /*
    Считывание зависимостей для файла.
     */
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
                            appStatus = new AppStatus(StatusCode.INVALID_REQUIREMENT, "Invalid requirement in file: " + getPathFromRoot(rootPath, tmp_file));
                            return;
                        }
                        dependencies.add(tmp_file);
                    }
                    fileDependencies.put(file, dependencies);
                }
                line = bf.readLine();
            }
        } catch (IOException e) {
            appStatus = new AppStatus(StatusCode.FILE_ERROR, e.getMessage());
        } catch (Exception e) {
            appStatus = new AppStatus(StatusCode.ERROR, e.getMessage());
        }
    }

    /*
    Сортировка файлов в зависимости от выбранной опции.
     */
    private void sortFiles() {
        if (sortMode == SortMode.TOPOLOGY) {
            graphInterpreter.sortGraph();
            appStatus = graphInterpreter.getGraphInterpreterStatus();
        } else if (sortMode == SortMode.PATH) {
            Collections.sort(files);
        }
    }

    /*
    Сохранение файлов в порядке сортировки.
     */
    private void saveSortedFiles() {
        try (FileWriter writer = new FileWriter(rootPath + File.separatorChar + "sorted.txt")) {
            for (var file : files) {
                writer.write(Files.readString(Path.of(file.getAbsolutePath())));
                writer.write("\n");
            }
        } catch (IOException e) {
            appStatus = new AppStatus(StatusCode.FILE_ERROR, e.getMessage());
        } catch (Exception e) {
            appStatus = new AppStatus(StatusCode.ERROR, e.getMessage());
        }
    }
}
