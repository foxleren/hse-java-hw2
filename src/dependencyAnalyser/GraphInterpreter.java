package dependencyAnalyser;

import java.io.File;
import java.util.*;

import static utils.PathUtil.getPathFromRoot;

public class GraphInterpreter {
    /*
    Путь до корневой директории.
     */
    private final String rootPath;

    /*
    Список файлов в директории и поддиректориях.
     */
    private List<File> files = new ArrayList<>();

    /*
    Список файлов и их зависимостей.
     */
    private Map<File, List<File>> fileDependencies = new HashMap<>();

    /*
    Индексы файлов, полученные в результате сортировки.
     */
    private final Stack<Integer> indexesOfSortedGraph = new Stack<>();

    /*
    Граф.
     */
    private static int[][] dependencyMatrix;

    /*
    Список, уже отмеченных вершин.
     */
    private boolean[] usedVerticals;

    private AppStatus graphInterpreterStatus = new AppStatus(StatusCode.OK, "OK");

    public Stack<Integer> getIndexesOfSortedGraph() {
        return indexesOfSortedGraph;
    }

    public AppStatus getGraphInterpreterStatus() {
        return graphInterpreterStatus;
    }

    public GraphInterpreter(String rootPath, List<File> files, Map<File, List<File>> fileDependencies) {
        this.rootPath = rootPath;
        this.files = files;
        this.fileDependencies = fileDependencies;
    }

    /*
    Построение графа.
     */
    public void buildGraph() {
        dependencyMatrix = new int[files.size()][files.size()];
        for (int i = 0; i < files.size(); i++) {
            for (int j = 0; j < files.size(); j++) {
                if (fileDependencies.containsKey(files.get(i))) {
                    var depends = fileDependencies.get(files.get(i));
                    if (depends.contains(files.get(j))) {
                        dependencyMatrix[i][j] = 1;
                    }
                    if (dependencyMatrix[i][j] == 1 && dependencyMatrix[j][i] == 1) {
                        graphInterpreterStatus = new AppStatus(StatusCode.FOUND_CYCLE, "Found cycle: " +
                                getPathFromRoot(rootPath, files.get(i)) + " -> " +
                                getPathFromRoot(rootPath, files.get(j)));
                        return;
                    }
                }
            }
        }
    }

    /*
    Сортировка графа.
     */
    public void sortGraph() {
        usedVerticals = new boolean[files.size()];
        for (int i = 0; i < files.size(); i++) {
            if (graphInterpreterStatus.statusCode() == StatusCode.FOUND_CYCLE) {
                return;
            }
            if (!usedVerticals[i]) {
                dfs(-1, -1, i);
            }
        }
    }

    /*
    Проход в глубину по графу.
     */
    private void dfs(int startIndex, int prevIndex, int index) {
        if (startIndex == index) {
            graphInterpreterStatus = new AppStatus(StatusCode.FOUND_CYCLE, "Found cycle: " +
                    getPathFromRoot(rootPath, files.get(prevIndex)) + " -> " +
                    getPathFromRoot(rootPath, files.get(startIndex)));
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
            indexesOfSortedGraph.push(index);
        }
    }
}
