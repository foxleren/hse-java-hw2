package dependencyAnalyser;

/*
Статус приложения, содержащий код состояния и сообщение.
 */
public record AppStatus(StatusCode statusCode, String message) {

    @Override
    public String toString() {
        return "Status: " + statusCode.toString() + " Message: " + message;
    }
}
