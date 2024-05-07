package online.inventory;

import io.grpc.stub.StreamObserver;
import org.online.server.grpc.generated.AddItemRequest;
import org.online.server.grpc.generated.AddItemServiceGrpc;
import org.online.server.grpc.generated.Item;
import org.online.server.grpc.generated.ItemManagerResponse;

public class AddItemService extends AddItemServiceGrpc.AddItemServiceImplBase implements DistributedTxListener{
    private final InventoryControlServer inventoryControlServer;

    public AddItemService(InventoryControlServer inventoryControlServer) {
        this.inventoryControlServer = inventoryControlServer;
    }

    @Override
    public void addItem(AddItemRequest request, StreamObserver<ItemManagerResponse> responseObserver) {
        String itemCode = request.getItemCode();
        if (inventoryControlServer.getItemWithCode(itemCode) != null) {
            ItemManagerResponse response = ItemManagerResponse.newBuilder().setMessage("Item already exists.").setStatus(false).build();
            responseObserver.onNext(response);
            responseObserver.onCompleted();
            return;
        }

        Item item = Item.newBuilder().setItemCode(itemCode).setItemName(request.getItemName()).setQuantity(request.getQuantity()).setUnitPrice(request.getUnitPrice()).build();

        inventoryControlServer.setItemWithCode(itemCode, item);

        ItemManagerResponse response = ItemManagerResponse.newBuilder().setItemName(item.getItemName()).setMessage("Item added successfully.").setStatus(true).build();

        responseObserver.onNext(response);
        responseObserver.onCompleted();
    }

    @Override
    public void onGlobalCommit() {

    }

    @Override
    public void onGlobalAbort() {

    }
}