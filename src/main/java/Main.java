public class Main {

    private static final int PORT = 6379;

    public static void main(String[] args) {
        new RedisServer(PORT).start();
    }
}
