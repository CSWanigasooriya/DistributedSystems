package online.inventory;

public interface DistributedTxListener {
    void onGlobalCommit();

    void onGlobalAbort();
}
