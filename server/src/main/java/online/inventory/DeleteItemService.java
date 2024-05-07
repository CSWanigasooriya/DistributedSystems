package online.inventory;

import io.grpc.stub.StreamObserver;
import org.online.server.grpc.generated.DeleteItemRequest;
import org.online.server.grpc.generated.DeleteItemServiceGrpc;
import org.online.server.grpc.generated.Item;
import org.online.server.grpc.generated.ItemManagerResponse;

public class DeleteItemService extends DeleteItemServiceGrpc.DeleteItemServiceImplBase implements DistributedTxListener{
    private final InventoryControlServer inventoryControlServer;

    public DeleteItemService(InventoryControlServer inventoryControlServer) {
        this.inventoryControlServer = inventoryControlServer;
    }

    @Override
    public void deleteItem(DeleteItemRequest request, StreamObserver<ItemManagerResponse> responseObserver) {
        String itemCode = request.getItemCode();
        Item item = inventoryControlServer.getItemWithCode(itemCode);
        if (item != null) {
            inventoryControlServer.removeItem(itemCode);
            ItemManagerResponse response = ItemManagerResponse.newBuilder().setItemName(item.getItemName()).setMessage("Item successfully deleted.").setStatus(true).build();
            responseObserver.onNext(response);
        } else {
            ItemManagerResponse response = ItemManagerResponse.newBuilder().setMessage("Item not found.").setStatus(false).build();
            responseObserver.onNext(response);
        }
        responseObserver.onCompleted();
    }

    @Override
    public void onGlobalCommit() {

    }

    @Override
    public void onGlobalAbort() {

    }
}