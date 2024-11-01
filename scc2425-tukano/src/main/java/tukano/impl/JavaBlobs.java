package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.ErrorCode.INTERNAL_ERROR;
import static tukano.api.Result.error;
import static tukano.api.Result.ErrorCode.FORBIDDEN;
import static tukano.api.Result.errorOrResult;

import java.nio.file.Path;
import java.sql.Blob;
import java.util.List;
import java.util.function.Consumer;
import java.util.logging.Logger;

import com.azure.core.http.rest.PagedIterable;
import com.azure.core.util.BinaryData;
import com.azure.storage.blob.BlobClient;
import com.azure.storage.blob.BlobContainerClient;
import com.azure.storage.blob.BlobContainerClientBuilder;
import com.azure.storage.blob.models.BlobItem;
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
	private String storageConnectionString = "DefaultEndpointsProtocol=https;AccountName=scc60333;AccountKey=uSsAte+zIBE7ksI9fHdxXTrVCNwDPdU+h3DeJQIDF4kbJNsy/dPheUg2o5bktO34tTYpvthyyIZU+AStbX1y8w==;EndpointSuffix=core.windows.net";
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
			BlobClient blob = containerClient.getBlobClient(toPath(blobId));

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
			BlobClient blob = containerClient.getBlobClient( toPath(blobId));

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
			BlobClient blob = containerClient.getBlobClient(toPath(blobId));

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

		if( ! Token.isValid( token, userId ) ) {
			Log.info(() -> format("No if do token"));
			return error(FORBIDDEN);
		}
		try {
			// Get client to blob
			List<BlobItem> it = containerClient.listBlobs().stream().toList();

			Log.info(() -> format(String.valueOf(it.size())));

			for(BlobItem b : it){
				Log.info(() -> format("TAMOS NO FOR"));
				BlobClient blob = containerClient.getBlobClient(b.getName());

				if(b.getName().contains(userId))
					blob.delete();
			}

			//blob.delete();

			return Result.ok();
		} catch( Exception e) {
			e.printStackTrace();
			return Result.error(INTERNAL_ERROR);
		}

		//return storage.delete( toPath(userId));
	}
	
	private boolean validBlobId(String blobId, String token) {		
		//System.out.println( toURL(blobId));
		//return Token.isValid(token, toURL(blobId));
		System.out.println( blobId);
		return Token.isValid(token, blobId);
	}

	private String toPath(String blobId) {
		return blobId.replace("+", "/");
	}
	
	private String toURL( String blobId ) {
		return baseURI + blobId ;
	}
}
