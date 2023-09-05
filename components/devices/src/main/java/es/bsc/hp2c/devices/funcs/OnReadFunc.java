package es.bsc.hp2c.devices.funcs;

public interface OnReadFunc extends Func{

    public void addOnReadFunction(Runnable action);

    public void onRead();
}
