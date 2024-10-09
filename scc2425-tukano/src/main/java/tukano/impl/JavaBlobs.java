package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.error;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.errorOrResult;

import java.nio.file.Path;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import tukano.api.Blobs;
import tukano.api.Result;
import tukano.impl.rest.TukanoRestServer;
import tukano.impl.storage.BlobStorage;
import tukano.impl.storage.FilesystemStorage;
import utils.Hash;
import utils.Hex;

public class JavaBlobs implements Blobs {
	
	private static Blobs instance;
	private static Logger Log = Logger.getLogger(JavaBlobs.class.getName());
	private String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=scc60333;AccountKey=Yu8V03aHeR2V8lEkbyt7NrSWUjKqqHZSKX+PnM7BbaANBgc1z1IX3o/zEDpL1ItaxYmEApUQwzWM+AStTp9s6Q==;EndpointSuffix=core.windows.net";
	private BlobContainerClient containerClient;

	public String baseURI;
	private BlobStorage storage;

	synchronized public static Blobs getInstance() {
		if( instance == null )
			instance = new JavaBlobs();
		return instance;
	}
	
	private JavaBlobs() {
		storage = new FilesystemStorage();
		baseURI = String.format("%s/%s/", TukanoRestServer.serverURI, Blobs.NAME);
		// Get container client
		this.containerClient = new BlobContainerClientBuilder()
				.connectionString(storageConnectionString)
				.containerName(Blobs.NAME)
				.buildClient();
	}
	
	@Override
	public Result<Void> upload(String blobId, byte[] bytes, String token) {
		Log.info(() -> format("upload : blobId = %s, sha256 = %s, token = %s\n", blobId, Hex.of(Hash.sha256(bytes)), token));
		if (!validBlobId(blobId, token))
			return error(FORBIDDEN);

		//Uploading to the external Azure blob storage service
		try {
			BinaryData data = BinaryData.fromBytes(bytes);

			// Get client to blob
			BlobClient blob = containerClient.getBlobClient( blobId);

			// Upload contents from BinaryData (check documentation for other alternatives)
			blob.upload(data);

			return Result.ok();

		} catch( Exception e) {
			e.printStackTrace();
			return Result.error(INTERNAL_ERROR);
		}


		//return storage.write( toPath( blobId ), bytes);
	}

	@Override
	public Result<byte[]> download(String blobId, String token) {
		Log.info(() -> format("download : blobId = %s, token=%s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		try {
			// Get client to blob
			BlobClient blob = containerClient.getBlobClient( blobId);

			// Download contents to BinaryData (check documentation for other alternatives)
			BinaryData data = blob.downloadContent();

			byte[] arr = data.toBytes();

			System.out.println( "Blob size : " + arr.length);

			return Result.ok(arr);
		} catch( Exception e) {
			e.printStackTrace();
			return Result.error(INTERNAL_ERROR);
		}
	}

	@Override
	public Result<Void> downloadToSink(String blobId, Consumer<byte[]> sink, String token) {
		Log.info(() -> format("downloadToSink : blobId = %s, token = %s\n", blobId, token));

		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		return storage.read( toPath(blobId), sink);
	}

	@Override
	public Result<Void> delete(String blobId, String token) {
		Log.info(() -> format("delete : blobId = %s, token=%s\n", blobId, token));
	
		if( ! validBlobId( blobId, token ) )
			return error(FORBIDDEN);

		try {
			// Get client to blob
			BlobClient blob = containerClient.getBlobClient( blobId);

			// Download contents to BinaryData (check documentation for other alternatives)
			blob.delete();

			return Result.ok();
		} catch( Exception e) {
			e.printStackTrace();
			return Result.error(INTERNAL_ERROR);
		}
	}
	
	@Override
	public Result<Void> deleteAllBlobs(String userId, String token) {
		Log.info(() -> format("deleteAllBlobs : userId = %s, token=%s\n", userId, token));

		if( ! Token.isValid( token, userId ) )
			return error(FORBIDDEN);
		
		return storage.delete( toPath(userId));
	}
	
	private boolean validBlobId(String blobId, String token) {		
		System.out.println( toURL(blobId));
		return Token.isValid(token, toURL(blobId));
	}

	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}
	
	private String toURL( String blobId ) {
		return baseURI + blobId ;
	}
}
