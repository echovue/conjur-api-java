package net.conjur.api.directory;

import java.io.IOException;
import java.net.URI;

import net.conjur.api.AuthenticatedClient;
import net.conjur.api.Endpoints;
import net.conjur.api.authn.Token;
import net.conjur.api.exceptions.http.ForbiddenException;
import net.conjur.api.exceptions.http.NotFoundException;
import net.conjur.util.Args;

import org.apache.http.client.methods.HttpUriRequest;
import org.apache.http.client.methods.RequestBuilder;

/**
 * This class provides access to the conjur directory services (sometimes refered to as
 * the "core" service).  All calls must be authenticated with an API token retrieved by 
 * an AuthnClient.
 */
public class DirectoryClient extends AuthenticatedClient {
	private static final String USERS_PATH = "/users";
	private static final String VARIABLES_PATH = "/variables";
	
	public DirectoryClient(Endpoints endpoints, Token token) {
		super(endpoints.directory(), token);
	}
	
	public DirectoryClient(URI endpoint, Token token){
		super(endpoint, token);
	}
	
	public DirectoryClient(String endpoint, Token token){
		super(endpoint, token);
	}
	
	/**
	 * Create a Conjur user with the given login and password.
	 * @param login The login for the new user, which may not be <code>null</code>
	 * @param password The password for the new user. If <code>null</code>, the created user will
	 * have an API key but no password.
	 * @return A {@link User} object representing the user created.
	 * @throws {@link ConjurApiException} when any error occurs
	 * @throws {@link ServerErrorException} when the user exists (subject to change...)
	 * @throws {@link ForbiddenException} when you don't have permission to create users
	 */
	public User createUser(final String login, final String password){
		Args.notBlank(login, "Login");
		
		if(password != null){
			Args.notBlank(password, "Password");
		}
		
		final RequestBuilder builder = requestBuilder("POST", USERS_PATH).addParameter("login", login);
		
		if(password != null){
			builder.addParameter("password", password);
		}
		
		return User.fromJson(execute(builder.build()));
	}

	/**
	 * Create a user without a password.
	 * @see #createUser(String, String)
	 */
	public User createUser(final String login){
		return createUser(login, null);
	}
	
	/**
	 * Return the conjur user with the given login.
	 * @param login The login, must not be <code>null</code> or blank.
	 * @return A {@link User} representing the conjur user with the given login.
	 * @throws {@link NotFoundException} when the user doesn't exist
	 * @throws {@link ForbiddenException} when you don't have permission to access the user
	 * @throws {@link ConjurApiException} when any error occurs
	 */
	public User getUser(String login){
		return User.fromJson(execute(request("GET", USERS_PATH + "/" + login)));
	}
	
	/**
	 * Like {@link #getUser(String)}, but returns <code>null</code> if the user does not exist
	 * instead of throwing.
	 * 
	 * @param login
	 * @return The user if it exists, otherwise <code>null</code>
	 * @throws {@link ConjurApiException}
	 */
	public User tryGetUser(String login){
		try{
			return getUser(login);
		}catch(NotFoundException e){
			return null;
		}
	}
	
	/**
	 * <p>Check to see whether a user with the given login exists.</p>
	 * 
	 * <p>This differs from checking for a <code>null</code> return from {@link #tryGetUser(String)}
	 * in that this method will return <code>true</code> if we don't have permission to access the specified
	 * user id instead of raising {@link ForbiddenException}.  This makes sense, because in that case creation
	 * will fail, for example if we tried to create a user named "admin".</p>
	 * 
	 * @param login the user login to check
	 * @return true if the user exists (specifically, if creating a user with this login will fail).
	 */
	public boolean userExists(String login){
		try{
			return tryGetUser(login) != null;
		}catch(ForbiddenException e){
			return true;
		}
	}
	
	/**
	 * Create an encrypted conjur variable.
	 * @param kind What kind of variable to create (for example, <code>"Secret Question"</code>, <code>"SSN4"</code>).
	 * @param mimeType The mime type that this variable will store (<code>"text/plain"</code> if <code>null</code>).
	 * @param id The id for this variable.  If <code>null</code>, a random id will be generated.  If given and a 
	 * 	variable with that id exists an exception is raised.
	 * @return A {@link Variable} representing the variable created.
	 * @throws {@link ConjurApiException}
	 */
	public Variable createVariable(
			final String kind, 
			final String mimeType, 
			final String id) {
		
		Args.notBlank(kind, "Kind");
		
		RequestBuilder builder = requestBuilder("POST", VARIABLES_PATH)
				.addParameter("mime_type", mimeType == null ? "text/plain" : mimeType)
				.addParameter("kind", kind);
		
		if(id != null){
			builder.addParameter("id", id);
		}
		
		return Variable.fromJson(execute(builder.build()), this);
	}
	
	/**
	 * Creates a variable with a randomly generated id.
	 * 
	 *  @see #createVariable(String, String, String)
	 */
	public Variable createVariable(String kind, String mimeType){
		return createVariable(kind, mimeType, null);
	}
	
	/**
	 * Creates a variable with a randomly generated id and mime type <code>"text/plain"</code>
	 * @see #createVariable(String, String, String) 
	 */
	public Variable createVariable(String kind){
		return createVariable(kind, "text/plain");
	}
	
	/**
	 * Return a variable with the given id, raising NotFoundException if it doesn't exist.
	 * @param id The variable's id, as returned by {@link Variable#getId()}
	 * @throws {@link NotFoundException} If the specified variable doesn't exist.
	 * @throws {@link ConjurApiException} When something else goes wrong
	 * @return A {@link Variable} object representing the variable retrieved.
	 */
	public Variable getVariable(String id){
		return Variable.fromJson(execute(request("GET", variablePath(id))), this);
	}
	
	/**
	 * Like {@link #getVariable(String)}, but returns null instead of throwing when 
	 * the variable is not found.
	 * @see #getVariable(String) 
	 */
	public Variable tryGetVariable(String id){
		try{
			return getVariable(id);
		}catch(NotFoundException e){
			return null;
		}
	}
	
	/**
	 * Check to see if the specified variable exists.
	 * 
	 * 
	 * <p>This differs from checking for a <code>null</code> return from {@link #tryGetVariable(String)}
	 * in that this method will return <code>true</code> if we don't have permission to access the specified
	 * variable instead of throwing a {@link ForbiddenException}.  This makes sense, because in that case creation
	 * will fail.</p>
	 * 
	 * @param variableId
	 * @param value
	 * @throws IOException
	 */
	public boolean variableExists(String id){
		try{
			return tryGetVariable(id) != null;
		}catch(ForbiddenException e){
			return true;
		}
	}
	
	
	void addVariableValue(String variableId, String value) {
		HttpUriRequest req = requestBuilder("POST", variableValuesPath(variableId))
				.addParameter("value", value)
				.build();
		execute(req);
	}
	
	String getVariableValue(String variableId){
		HttpUriRequest req = request("GET", variableValuePath(variableId));
		return execute(req);
	}
	
	String getVariableValue(String variableId, int version){
		HttpUriRequest req = requestBuilder("GET",  variableValuePath(variableId))
				.addParameter("version", String.valueOf(version))
				.build();
		return execute(req);
	}
	 

	private String variableValuesPath(String variableId){ 
		return variablePath(variableId) + "/values";
 	}
	
	
	private String variableValuePath(String variableId){ 
		return variablePath(variableId) + "/value";
 	}
	
	private String variablePath(String variableId){
		return VARIABLES_PATH + "/" + variableId;
	}

	@Override
	protected String getEndpointFromEndpoints(Endpoints endpoints) {
		return endpoints.directory();
	}
}