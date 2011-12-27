/**
 * Copyright 2011 Kurtis L. Nusbaum
 * 
 * This file is part of UDJ.
 * 
 * UDJ is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 2 of the License, or
 * (at your option) any later version.
 * 
 * UDJ is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 * 
 * You should have received a copy of the GNU General Public License
 * along with UDJ.  If not, see <http://www.gnu.org/licenses/>.
 */
package org.klnusbaum.udj.network;

import android.util.Log;
import android.location.Location;

import java.util.List;
import java.util.HashMap;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Date;
import java.net.URI;
import java.net.URISyntaxException;

import org.apache.http.params.BasicHttpParams;
import org.apache.http.HttpVersion;
import org.apache.http.conn.scheme.Scheme;
import org.apache.http.conn.scheme.SchemeRegistry;
import org.apache.http.conn.scheme.PlainSocketFactory;
import org.apache.http.params.HttpProtocolParams;
import org.apache.http.impl.conn.tsccm.ThreadSafeClientConnManager;
import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.HttpStatus;
import org.apache.http.auth.AuthenticationException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.entity.StringEntity;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.client.methods.HttpDelete;
import org.apache.http.NameValuePair;
import org.apache.http.ParseException;
import org.apache.http.client.HttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.conn.params.ConnManagerParams;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.params.HttpConnectionParams;
import org.apache.http.params.HttpParams;
import org.apache.http.util.EntityUtils;

import org.json.JSONArray;
import org.json.JSONObject;
import org.json.JSONException;


import org.klnusbaum.udj.containers.LibraryEntry;
import org.klnusbaum.udj.containers.PlaylistEntry;
import org.klnusbaum.udj.containers.Event;

/**
 * A connection to the UDJ server
 */
public class ServerConnection{
  
  private static final String TAG = "ServerConnection";
  private static final String PARAM_USERNAME = "username";

  private static final String PARAM_PASSWORD = "password";

  private static final String PARAME_EVENT_NAME = "name";
  /** 
   * This port number is a memorial to Keith Nusbaum, my father. I loved him
   * deeply and he was taken from this world far too soon. Never-the-less 
   * we all continue to benefit from his good deeds. Without him, I wouldn't 
   * be here, and there would be no UDJ. Please, don't change this port 
   * number. Keep the memory of my father alive.
   * K = 10 % 10 = 0
   * e = 4  % 10 = 4
   * i = 8  % 10 = 8
   * t = 19 % 10 = 9
   * h = 7  % 10 = 7
   * Port 4897, the Keith Nusbaum Memorial Port
   */
  private static final int SERVER_PORT = 4897;

  private static final String NETWORK_PROTOCOL = "http";
 
  private static final String SERVER_HOST = "10.0.2.2";

 
  private static final String TICKET_HASH_HEADER = "X-Udj-Ticket-Hash";
  private static final String USER_ID_HEADER = "X-Udj-User-Id";
 
  private static final int REGISTRATION_TIMEOUT = 30 * 1000; // ms
  
  private static final String AVAILABLE_QUERY_PARAM = "query";

  private static DefaultHttpClient httpClient;

  public static DefaultHttpClient getHttpClient(){
    if(httpClient == null){
      SchemeRegistry schemeReg = new SchemeRegistry();
      schemeReg.register(
        new Scheme("http", PlainSocketFactory.getSocketFactory(), SERVER_PORT));
      BasicHttpParams params = new BasicHttpParams();
      ConnManagerParams.setMaxTotalConnections(params, 100);
      HttpProtocolParams.setVersion(params, HttpVersion.HTTP_1_1);
      HttpProtocolParams.setUseExpectContinue(params, true);
      ThreadSafeClientConnManager cm = new ThreadSafeClientConnManager(
        params, schemeReg);
      httpClient = new DefaultHttpClient(cm, params);
    }
    return httpClient;
  }

  public static class AuthResult{
    public String ticketHash;
    public long userId;
    
    public AuthResult(String ticketHash, long userId){
      this.ticketHash = ticketHash;
      this.userId = userId;
    }
  }

  public static AuthResult authenticate(String username, String password)
    throws AuthenticationException, IOException
  {
    URI AUTH_URI = null;
    try{
      AUTH_URI = new URI(
        NETWORK_PROTOCOL, "", SERVER_HOST, SERVER_PORT, "/udj/auth", null, null);
    }
    catch(URISyntaxException e){
      //TODO should never get here but I should do something if it does.
    }
    final ArrayList<NameValuePair> params = new ArrayList<NameValuePair>();
    params.add(new BasicNameValuePair(PARAM_USERNAME, username));
    params.add(new BasicNameValuePair(PARAM_PASSWORD, password));
    boolean authWorked = false;
    HttpEntity entity = null;
    entity = new UrlEncodedFormEntity(params);
    final HttpPost post = new HttpPost(AUTH_URI);
    post.addHeader(entity.getContentType());
    post.setEntity(entity);
    final HttpResponse resp = getHttpClient().execute(post);
    if(resp.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED){
      throw new AuthenticationException();
    }
    else if(!resp.containsHeader(TICKET_HASH_HEADER)){
      throw new IOException("No ticket hash header was found in resposne");
    }
    else{
      return new AuthResult(
        resp.getHeaders(TICKET_HASH_HEADER)[0].getValue(),
        Long.valueOf(resp.getHeaders(USER_ID_HEADER)[0].getValue()));
    }
  }

  public static List<LibraryEntry> availableMusicQuery(
    String query,
    int eventId,
    String ticketHash)
    throws JSONException, ParseException, IOException, AuthenticationException
  {
    try{
      URI queryUri = new URI(
        NETWORK_PROTOCOL, "", SERVER_HOST, SERVER_PORT, 
        "/udj/events/" + eventId + "/available_music", 
        AVAILABLE_QUERY_PARAM + "=" +query, "");
      JSONArray searchResults = new JSONArray(doGet(queryUri, ticketHash));
      return LibraryEntry.fromJSONArray(searchResults);
    }
    catch(URISyntaxException e){
      return null;
      //TDOD inform caller that theire query is bad 
    }
  }

  /*public static String doPost(ArrayList<NameValuePair> params, String uri)
    throws AuthenticationException, IOException
  {
    String toReturn = null;
    HttpEntity entity = null;
    entity = new UrlEncodedFormEntity(params);
    final HttpPost post = new HttpPost(uri);
    post.addHeader(entity.getContentType());
    post.setEntity(entity);
    final HttpResponse resp = getHttpClient().execute(post);
    final String response = EntityUtils.toString(resp.getEntity());
    if(resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
      toReturn = response;
    } 
    else if(resp.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED){
      throw new AuthenticationException();
    }
    else{
      throw new IOException();
    }
    return toReturn;
  }*/

  public static String doGet(URI uri, String ticketHash)
    throws AuthenticationException, IOException
  {
    final HttpGet get = new HttpGet(uri);
    get.addHeader(TICKET_HASH_HEADER, ticketHash);
    final HttpResponse resp = getHttpClient().execute(get);
    final String response = EntityUtils.toString(resp.getEntity());
    Log.v(TAG, "Response \n" + response.substring(response.length()/10));
    if(resp.getStatusLine().getStatusCode() == HttpStatus.SC_OK){
      return response;
    }
    else if(resp.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED){
      throw new AuthenticationException();
    }
    else{
      throw new IOException(response);
    }
  }

  public static String doPut(URI uri, String ticketHash, String payload)
    throws AuthenticationException, IOException
  {
    String toReturn = null;
    final HttpPut put = new HttpPut(uri);
    put.addHeader(TICKET_HASH_HEADER, ticketHash);
    if(payload != null){
      StringEntity entity = new StringEntity(payload);
      entity.setContentType("text/json");
      put.addHeader(entity.getContentType());
      put.setEntity(entity);
    }
    final HttpResponse resp = getHttpClient().execute(put);
    final String response = EntityUtils.toString(resp.getEntity());
    if(resp.getStatusLine().getStatusCode() == HttpStatus.SC_CREATED){
      toReturn = response;
    } 
    else if(resp.getStatusLine().getStatusCode() == HttpStatus.SC_UNAUTHORIZED){
      throw new AuthenticationException();
    }
    else{
      //TODO probably shouldn't be throwing an IOException as that really 
      //doesn't describe what went wrong.
      Log.e(TAG, "Error doing put: " + response);
      throw new IOException();
    }
    return toReturn;
  }

  public static void doDelete(URI uri, String ticketHash)
    throws IOException, AuthenticationException
  {
    final HttpDelete delete = new HttpDelete(uri);
    delete.addHeader(TICKET_HASH_HEADER, ticketHash);
    final HttpResponse resp = getHttpClient().execute(delete);
    if(resp.getStatusLine().getStatusCode() != HttpStatus.SC_OK){
      //TODO probably shouldn't be throwing an IOException as that really 
      //doesn't describe what went wrong.
      throw new IOException("Failed to execute delete got status code of "+
        resp.getStatusLine().getStatusCode());
    }
  }

  public static List<Event> getNearbyEvents(
    Location location, String ticketHash)
    throws
    JSONException, ParseException, IOException, AuthenticationException
  {
    if(location == null) return null;
    try{
      URI eventsQuery = new URI(
        NETWORK_PROTOCOL, "", SERVER_HOST, SERVER_PORT, 
        "/udj/events/" + location.getLatitude() + "/" + location.getLongitude(),
        null, null);
      JSONArray events = new JSONArray(doGet(eventsQuery, ticketHash));
      return Event.fromJSONArray(events);
    }
    catch(URISyntaxException e){
      return null;
      //TDOD inform caller that theire query is bad 
    }
  }

  public static List<Event> searchForEvents(
    String query, String ticketHash)
    throws
    JSONException, ParseException, IOException, AuthenticationException
  {
    try{
      URI eventsQuery = new URI(
        NETWORK_PROTOCOL, "", SERVER_HOST, SERVER_PORT, 
        "/udj/events/",
        PARAME_EVENT_NAME+"="+query, null);
      JSONArray events = new JSONArray(doGet(eventsQuery, ticketHash));
      return Event.fromJSONArray(events);
    }
    catch(URISyntaxException e){
      return null;
      //TDOD inform caller that theire query is bad 
    }
  }

  public static boolean joinEvent(final long eventId, String ticketHash)
    throws IOException
  {
    try{
      URI uri  = new URI(
        NETWORK_PROTOCOL, "", SERVER_HOST, SERVER_PORT, 
        "/udj/events/" + eventId + "/user",
        null, null);
       doPut(uri, ticketHash, null); 
    }
    catch(URISyntaxException e){
      Log.e("Server conn", "URI syntax error in join event");
      return false; 
    }
    catch(AuthenticationException e){
      Log.e("Server conn", "Auth error in join event");
      return false;
    }
    catch(IOException e){
      Log.e("Server conn", "IO eeror in join event");
      return false;
    }
    return true;
  }

  public static List<PlaylistEntry> getActivePlaylist(long eventId, 
    String authToken)
    throws JSONException, ParseException, IOException, AuthenticationException
  {
    try{
      URI uri = new URI(
        NETWORK_PROTOCOL, "", SERVER_HOST, SERVER_PORT, 
        "/udj/events/"+eventId+"/active_playlist",
        null, null);
      JSONArray playlistEntries = new JSONArray(doGet(uri, authToken));
      return PlaylistEntry.fromJSONArray(playlistEntries);
    }
    catch(URISyntaxException e){
      return null;
      //TDOD inform caller that theire query is bad 
    }
  }

  public static void leaveEvent(long eventId, long userId, String authToken)
    throws IOException, AuthenticationException
  {
    try{
      URI uri = new URI(
        NETWORK_PROTOCOL, "", SERVER_HOST, SERVER_PORT, 
        "/udj/events/"+eventId+"/users/"+userId,
        null, null);
      doDelete(uri, authToken);
    }
    catch(URISyntaxException e){
      //TDOD inform caller that theire query is bad 
    }

  }

  public static List<LibraryEntry> availableMusicQuery(
    String query, long eventId, String authToken)
    throws JSONException, ParseException, IOException, AuthenticationException
  {
    try{
      URI uri = new URI(
        NETWORK_PROTOCOL, "", SERVER_HOST, SERVER_PORT,
        "/udj/events/"+eventId+"/available_music",
        "query="+query, null);
      JSONArray libEntries = new JSONArray(doGet(uri, authToken));
      return LibraryEntry.fromJSONArray(libEntries);
    }
    catch(URISyntaxException e){
      //TDOD inform caller that theire query is bad 
    }
    return null;
  }

  public static void addSongsToActivePlaylist(
    HashMap<Long, Long> requests, long eventId, String authToken)
    throws JSONException, ParseException, IOException, AuthenticationException
  {
    try{
      URI uri = new URI(
        NETWORK_PROTOCOL, "", SERVER_HOST, SERVER_PORT,
        "/udj/events/"+eventId+"/active_playlist/songs",
        null, null);
      String payload = getAddToActivePlaylistJSON(requests).toString();
      Log.d(TAG, "Add songs to active playlist payload");
      Log.d(TAG, payload);
      doPut(uri, authToken, payload); 
    }
    catch(URISyntaxException e){
      //TDOD inform caller that theire query is bad 
    }
  }

  private static JSONArray getAddToActivePlaylistJSON(
    HashMap<Long, Long> requests) throws JSONException
  
  {
    JSONArray toReturn = new JSONArray();
    for(Long requestId : requests.keySet()){
      JSONObject requestObject = new JSONObject();
      requestObject.put("client_request_id", requestId);
      requestObject.put("lib_id", requests.get(requestId));
      toReturn.put(requestObject);
    }
    return toReturn;
  }

  public static HashMap<Long,Long> getAddRequests(
    long userId, long eventId, String authToken)
    throws JSONException, ParseException, IOException, AuthenticationException
  {
    try{
      URI uri = new URI(
        NETWORK_PROTOCOL, "", SERVER_HOST, SERVER_PORT,
        "/udj/events/"+eventId+"/active_playlist/"+userId + "/add_requests",
        null, null);
      return getRequestsHashMap(new JSONArray(doGet(uri, authToken)));
    }
    catch(URISyntaxException e){
      //TDOD inform caller that theire query is bad 
    }
    return null;
  }

  private static HashMap<Long, Long> getRequestsHashMap(JSONArray requests)
    throws JSONException
  {
    HashMap<Long, Long> toReturn = new HashMap<Long, Long>();
    for(int i=0; i<requests.length(); ++i){
      JSONObject jObj = requests.getJSONObject(i);
      toReturn.put(jObj.getLong("client_request_id"), jObj.getLong("lib_id"));
    }
    return toReturn;
  }
}
