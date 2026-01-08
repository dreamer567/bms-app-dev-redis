package jp.adsur.exception;

public class ScimException extends RuntimeException {
    private final int status;

    public ScimException(int status, String message) {
        super(message);
        this.status = status;
    }
    public int getStatus() { return status; }
}
