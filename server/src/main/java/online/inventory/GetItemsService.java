package online.inventory;

import io.grpc.stub.StreamObserver;
import online.inventory.grpc.generated.GetItemsRequest;
import online.inventory.grpc.generated.GetItemsResponse;
import online.inventory.grpc.generated.GetItemsServiceGrpc;
import online.inventory.grpc.generated.Item;


import java.util.List;

public class GetItemsService extends GetItemsServiceGrpc.GetItemsServiceImplBase implements DistributedTxListener{
    private final InventoryControlServer inventoryControlServer;

    public GetItemsService(InventoryControlServer inventoryControlServer) {
        this.inventoryControlServer = inventoryControlServer;
    }

    public void getItems(GetItemsRequest request, StreamObserver<GetItemsResponse> responseObserver) {
        List<Item> allItems = inventoryControlServer.getAllItems();

        GetItemsResponse response = GetItemsResponse
                .newBuilder()
                .addAllItems(allItems)
                .build();

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
