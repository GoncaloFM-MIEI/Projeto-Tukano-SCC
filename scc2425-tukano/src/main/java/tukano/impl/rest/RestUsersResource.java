package tukano.impl.rest;

import java.util.List;

import jakarta.inject.Singleton;
import tukano.api.User;
import tukano.api.Users;
import tukano.api.rest.RestUsers;
import tukano.impl.JavaUsers;

@Singleton
public class RestUsersResource extends RestResource implements RestUsers {

	final Users impl;
	public RestUsersResource() {
		this.impl = JavaUsers.getInstance();
	}
	
	@Override
	public String createUser(User user, boolean hasCache) {
		return super.resultOrThrow( impl.createUser( user, hasCache));
	}

	@Override
	public User getUser(String name, String pwd, boolean hasCache) {
		return super.resultOrThrow( impl.getUser(name, pwd, hasCache));
	}
	
	@Override
	public User updateUser(String name, String pwd, User user, boolean hasCache) {
		return super.resultOrThrow( impl.updateUser(name, pwd, user, hasCache));
	}

	@Override
	public User deleteUser(String name, String pwd,	boolean hasCache) {
		return super.resultOrThrow( impl.deleteUser(name, pwd, hasCache));
	}

	@Override
	public List<User> searchUsers(String pattern, boolean hasCache) {
		return super.resultOrThrow( impl.searchUsers( pattern, hasCache));
	}
}
