package tukano.impl;

import static java.lang.String.format;
import static tukano.api.Result.error;
import static tukano.api.Result.errorOrResult;
import static tukano.api.Result.errorOrValue;
import static tukano.api.Result.ok;
import static tukano.api.Result.ErrorCode.BAD_REQUEST;
import static tukano.api.Result.ErrorCode.FORBIDDEN;

import java.util.List;
import java.util.Locale;
import java.util.concurrent.Executors;
import java.util.logging.Logger;

import com.azure.cosmos.CosmosContainer;
import com.fasterxml.jackson.annotation.JsonAlias;
import tukano.api.Result;
import tukano.api.User;
import tukano.api.Users;
import tukano.impl.data.UserDAO;
import utils.DB;
import utils.CosmosDBLayer;

public class JavaUsers implements Users {
	
	private static Logger Log = Logger.getLogger(JavaUsers.class.getName());

	private static Users instance;

	private static CosmosDBLayer cosmos;

	private static CosmosContainer usersContainer;
	
	synchronized public static Users getInstance() {
		if( instance == null )
			instance = new JavaUsers();
		cosmos = CosmosDBLayer.getInstance();
		usersContainer = cosmos.getDB().getContainer(Users.NAME);
		return instance;
	}
	
	private JavaUsers() {
		
	}



	@Override
	public Result<String> createUser(User user) {

		Log.info(() -> format("createUser : %s\n", user));
		if( badUserInfo( user ) ) {
			return error(BAD_REQUEST);
		}
		Locale.setDefault(Locale.US);
		return errorOrValue( cosmos.insertOne(user, usersContainer), user.getUserId() );
	}

	@Override
	public Result<User> getUser(String userId, String pwd) {
		Log.info( () -> format("getUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null)
			return error(BAD_REQUEST);
		
		return validatedUserOrError( cosmos.getOne( userId, User.class, usersContainer), pwd);
	}

	@Override
	public Result<User> updateUser(String userId, String pwd, User other) {
		Log.info(() -> format("updateUser : userId = %s, pwd = %s, user: %s\n", userId, pwd, other));

		if (badUpdateUserInfo(userId, pwd, other))
			return error(BAD_REQUEST);

		return errorOrResult( validatedUserOrError(cosmos.getOne( userId, User.class, usersContainer), pwd), user -> cosmos.updateOne( user.updateFrom(other), usersContainer));
	}

	@Override
	public Result<User> deleteUser(String userId, String pwd) {
		Log.info(() -> format("deleteUser : userId = %s, pwd = %s\n", userId, pwd));

		if (userId == null || pwd == null )
			return error(BAD_REQUEST);

		Result<User> res = cosmos.getOne(userId, User.class, usersContainer);

		//TODO ESCLARECER A SITUACÂO DO RETURN
		return errorOrResult( validatedUserOrError(res, pwd), user -> {

			// Delete user shorts and related info asynchronously in a separate thread
			Executors.defaultThreadFactory().newThread( () -> {
				JavaBlobs.getInstance().deleteAllBlobs(userId, Token.get(userId));
				JavaShorts.getInstance().deleteAllShorts(userId, pwd, Token.get(userId));
			}).start();

			//Result<User> res = cosmos.getOne(userId, User.class);
			cosmos.deleteOne(user, usersContainer);

			//	cosmos.getOne( userId, User.class);

			return res;
		});
	}

	@Override
	public Result<List<User>> searchUsers(String pattern) {
		Log.info( () -> format("searchUsers : patterns = %s\n", pattern));

		//TODO TEMOS MESMO QUE TER O .TOUPPSERCASE() no patter? Não funciona com ele :)
		var query = format("SELECT * FROM users u WHERE u.id LIKE '%%%s%%'", pattern);
		var hits = cosmos.query(User.class, query, usersContainer);
				//.stream()
				//.map(User::copyWithoutPassword)
				//.toList();

		return ok(hits.value().stream().toList());
	}

	
	private Result<User> validatedUserOrError( Result<User> res, String pwd ) {
		if( res.isOK())
			return res.value().getPwd().equals( pwd ) ? res : error(FORBIDDEN);
		else
			return res;
	}
	
	private boolean badUserInfo( User user) {
		return (user.userId() == null || user.pwd() == null || user.displayName() == null || user.email() == null);
	}
	
	private boolean badUpdateUserInfo( String userId, String pwd, User info) {
		return (userId == null || pwd == null || info.getUserId() != null && ! userId.equals( info.getUserId()));
	}
}
