/*

	Nguyen Hoang Viet _ 18020063
	Server

*/
import java.io.*;
import java.net.*;
import java.nio.*;
import java.math.*;
import java.lang.*;
import java.util.*;
import java.text.SimpleDateFormat;

import javax.sound.sampled.*;

public class Server {
	static int PORT = 9000;

	static byte SOUND = 16;
	static byte GROUP = 8;
	static byte UPLOAD = 4;
	static byte DOWNLOAD = 2;
	static byte MESSAGE = 1;

	static Map<Integer, SocketThread> users = new HashMap<>();
	static Map<Integer, Group> groups = new HashMap<>();
	static Map<Integer, List<StoreFile> > fileMapping = new HashMap<>();
	
	static Map<Integer, SocketThread> voiceOver = new HashMap<>();
	static Map<Integer, List<Integer>> voiceInvt = new HashMap<>();

	static synchronized boolean login(int id, SocketThread thread) {
		if(id <= 0 || id >= 1000 || users.containsKey(Integer.valueOf(id))) return false;
		users.put(Integer.valueOf(id), thread);
		System.out.println("Logged in:" + id);
		return true;
	}

	static synchronized void logout(int id) {
		users.remove(Integer.valueOf(id));
	}

	static class StoreFile {
		public String fileName;
		public String systemName;

		StoreFile() {

		}

		StoreFile(String fileName, String systemName) {
			this.fileName = fileName;
			this.systemName = systemName;
		}
	}

	static class SocketThread extends Thread {
		private Socket client;
		private int id = -1;
		SocketThread(Socket _client) {
			client = _client;
		}
		synchronized void send(int sender, boolean group, int destID, String message, boolean upload, boolean download, boolean voice) {
			byte[] data = new byte[message.length() + 13];
			int length = message.length();
			int status = 0;
			if(group) status = status | GROUP;
			if(upload) status = status | UPLOAD;
			if(download) status = status | DOWNLOAD;
			if(voice) status = status | SOUND;
 			if(!upload && !download && !voice) status = status | MESSAGE;

			data[0] = (byte)(length % 128);
			length /= 128;
			data[1] = (byte)(length % 128);
			length /= 128;
			data[2] = (byte)(length % 128);
			length /= 128;
			data[3] = (byte)(length % 128);
			length /= 128;

			data[4] = (byte)(sender % 128);
			sender /= 128;
			data[5] = (byte)(sender % 128);
			sender /= 128;
			data[6] = (byte)(sender % 128);
			sender /= 128;
			data[7] = (byte)(sender % 128);
			sender /= 128;

			data[8] = (byte)(destID % 128);
			destID /= 128;
			data[9] = (byte)(destID % 128);
			destID /= 128;
			data[10] = (byte)(destID % 128);
			destID /= 128;
			data[11] = (byte)(destID % 128);
			destID /= 128;

			data[12] = (byte)status;
			char[] d = message.toCharArray();
			byte[] mess = new byte[d.length];
			for(int i=0;i<d.length;i++) mess[i] = (byte)d[i];
			for(int i=0;i<message.length();i++) data[i+13] = mess[i];

			try {
				DataOutputStream writer = new DataOutputStream(client.getOutputStream());
				writer.write(data);
			}
			catch(Exception ex) {
				ex.printStackTrace();
			}
		}	

		synchronized void send(int sender, boolean group, int destID, String message, boolean upload, boolean download) {
			send(sender,group,destID, message, upload, download, false);
		}	

		synchronized void sendVoice(byte[] voice, int length) {
			int status = 0;
			status = status | SOUND;
			
			byte[] mess = voice;
			int perma = length;
			byte[] data = new byte[length + 13];

			data[0] = (byte)(length % 128);
			length /= 128;
			data[1] = (byte)(length % 128);
			length /= 128;
			data[2] = (byte)(length % 128);
			length /= 128;
			data[3] = (byte)(length % 128);
			length /= 128;
		
			data[4] = 0;
			data[5] = 0;
			data[6] = 0;
			data[7] = 0;

			data[8] = 0;
			data[9] = 0;
			data[10] = 0;
			data[11] = 0;

			data[12] = (byte)status;

			for(int i=0;i<perma;i++) data[i+13] = mess[i];
			try {
				DataOutputStream writer = new DataOutputStream(client.getOutputStream());
				writer.write(data);
			}
			catch(Exception ex) {
				ex.printStackTrace();
			}
		}

		synchronized boolean upload(String fileName) {
		try {
			DataOutputStream writer = new DataOutputStream(client.getOutputStream());
			File file = new File(fileName);
			int fileSize = (int)file.length();
			byte[] fileLength = new byte[8];
			for(int i=0;i<8;i++) {
				fileLength[i] = (byte)(fileSize % 128);
				fileSize /= 128;
			}
			writer.write(fileLength);
			byte[] fileData = new byte[2048];
			fileSize = (int)file.length();
			int sent = 0;
			FileInputStream fis = new FileInputStream(file);
			while(sent < fileSize) {
				int r = fis.read(fileData);
				if(r < 2048) {
					writer.write(fileData,0,r);
				}
				else writer.write(fileData);
				sent += r;
			}				
			writer.flush();
			return true;
		}
		catch(Exception ex) {
			return false;
		}
	}

		public void run() {
			try {
				int lengthCounter = 0;
				int senderCounter = 0;
				int destCounter = 0;

				int messLength = 0;
				int senderID = 0;
				int destID = 0;

				int status = 512;

				InputStream inputStream = client.getInputStream();
				StringBuilder messBuffer = new StringBuilder();
				byte[] data = new byte[3000];
				ByteBuffer buffer = ByteBuffer.allocate(3000);
				while(true) {
					int length = inputStream.read(data);
					if(length < 0) {
						break;
					}
					for(int i = 0 ; i < length ; i++) {
						if(lengthCounter < 4) {
							messLength = messLength + data[i] * (int) Math.pow(128, lengthCounter);
							lengthCounter ++;
						} else
						if(senderCounter < 4) {
							senderID = senderID + data[i] * (int) Math.pow(128, senderCounter);
							senderCounter ++;
						} else 
						if(destCounter < 4) {
							destID = destID + data[i] * (int) Math.pow(128, destCounter);
							destCounter ++;
						} else 
						if(status == 512) {
							status = data[i];
						} else {
							messBuffer.append((char) data[i]);

							if((status & SOUND) == SOUND) buffer.put(data[i]);
							if(messBuffer.length() == messLength) {
								if(id == -1) {
									try {
										id = Integer.parseInt(messBuffer.toString());
										if(login(id,this))
											send(0,false,0,"210 Authenticated",false,false);
										else{
											send(0,false,0,"410 Invalid ID",false,false);
											id = -1;
										}
									} catch(Exception ex) {
										send(0,false,0,"411 Invalid ID",false,false);
									}
								}
								else {
									if((status & MESSAGE) == MESSAGE) {
										if((status & GROUP) == GROUP) {
											String message = messBuffer.toString();
											if(destID == 0) {
												if(message.startsWith("CREATE ")) {
													try {
														Integer temp = Integer.parseInt(message.substring(7));
														if(groups.containsKey(temp) || temp.intValue() > 10000 || temp.intValue() < 1 ) {
															send(0,false,0,"440 Invalid ID to create group", false,false);
														}
														else {
															groups.put(temp,new Group(temp.intValue()));
														}
													}
													catch(Exception ex) {
														send(0,false,0,"441 Invalid ID to create group", false,false);
													}
												}
											} else 
											if(!groups.containsKey(Integer.valueOf(destID))) {
												send(0,false,0,"412 Invalid group ID",false,false);
											}
											else {
												if(message.equals("LEAVE")) {
													groups.get(Integer.valueOf(destID)).leave(this);
													send(0,false,0, "Leaved group " + destID,false,false);
												} else
												if(message.equals("JOIN")) {
													groups.get(Integer.valueOf(destID)).join(this);
													send(0, true, destID, "Joined group",false,false);
												} else 
												if(groups.get(Integer.valueOf(destID)).usersInGroup.contains(this)) { 
													if(message.equals("LIST")) {
														String list = "LIST FILE OF GROUP " + destID + " :\n";
														Group group = groups.get(Integer.valueOf(destID));
														for(int index = 0;index < group.storeFile.size(); index++) {
															list = list + " " + index + " : " + group.storeFile.get(index).fileName + " \n";
														}
														send(0,false,0,list,false,false);
													} else
													groups.get(Integer.valueOf(destID)).broadcast(id, messBuffer.toString());
												} else {
													send(0,false,0,"You havent joined this group yet", false, false);
												}
											}
										}
										else {
											if(!users.containsKey(Integer.valueOf(destID))) {
												send(0,false,0,"414 Invalid user ID",false,false);
											}
											else {
												if(messBuffer.toString().equals("LIST")) {
													int chatID = Math.min(id,destID) * 2000 + Math.max(id,destID);
													String fileList = "LIST FILE OF " + destID + " : \n";
													if(!fileMapping.containsKey(Integer.valueOf(chatID))) fileMapping.put(Integer.valueOf(chatID), new ArrayList<>());
													List<StoreFile> storage = fileMapping.get(Integer.valueOf(chatID));
													for(int index = 0; index < storage.size();index++) {
														fileList = fileList + " " + index + " : " + storage.get(index).fileName + " \n";
													}
													send(0,false,0,fileList,false,false);
												} else
												if(messBuffer.toString().equals("ACK")) {
													if(voiceInvt.get(Integer.valueOf(id)) == null) voiceInvt.put(Integer.valueOf(id), new ArrayList<Integer>());
													if(voiceInvt.get(Integer.valueOf(id)).indexOf(Integer.valueOf(destID)) != -1) {
														if(voiceOver.get(Integer.valueOf(destID)) != null || voiceOver.get(Integer.valueOf(id)) != null) {
															send(0,false,0,"449 Line busy", false, false);
														} 
														else {
															voiceOver.put(Integer.valueOf(destID), this);
															voiceOver.put(Integer.valueOf(id), users.get(Integer.valueOf(destID)));
															users.get(Integer.valueOf(destID)).send(id,false,0, "250 Voice start",false,false);
															send(0,false,0, "250 Voice start",false,false);
														}
													}
													else {
														send(0,false,0,"450 No invitation",false,false);
													}

												} else if(messBuffer.toString().equals("TER")) {
													System.out.println("TER Handler");
													if(voiceOver.get(Integer.valueOf(destID)) == this) {
														voiceOver.put(Integer.valueOf(destID), null);
														voiceOver.put(Integer.valueOf(id), null);
														users.get(Integer.valueOf(destID)).send(id,false,0, "Conversation ended",false,false);
														send(0,false,0, "Conversation ended",false,false);
													}
												} else if(messBuffer.toString().equals("Voice message")) {
													users.get(Integer.valueOf(destID)).send(id, false, 0, messBuffer.toString(), false, false);
													if(voiceInvt.get(Integer.valueOf(destID)) == null) voiceInvt.put(Integer.valueOf(destID),new ArrayList<>());
													if(voiceInvt.get(Integer.valueOf(destID)).indexOf(id) == -1) voiceInvt.get(Integer.valueOf(destID)).add(Integer.valueOf(id));
												} else
												users.get(Integer.valueOf(destID)).send(id,false,0, messBuffer.toString(),false,false);
											}
										}
									}
									else if((status & UPLOAD) == UPLOAD) {
										String fileName = messBuffer.toString();
										String timeStamp = new SimpleDateFormat("yyyy.MM.dd.HH.mm.ss").format(new java.util.Date());
										String autoGen = null;
										if((status & GROUP) == GROUP) {
											if(!groups.containsKey(Integer.valueOf(destID))) {
												send(0,false,0,"411 Invalid group ID",false,false);
											}
											else {
												if(groups.get(Integer.valueOf(destID)).usersInGroup.contains(this)) {
													send(0,false,0,fileName,true,false);
													autoGen = "UID_G_" + destID + "_" + id + "_" + timeStamp + "_" + fileName;
													groups.get(Integer.valueOf(destID)).storeFile.add(new StoreFile(fileName,autoGen));
												} else {
													send(0,false,0,"You havent joined this group yet", false, false);
												}
											}
										}
										else {
											if(!users.containsKey(Integer.valueOf(destID))) {
												send(0,false,0,"412 Invalid user ID",false,false);
											}
											else {
												int chatID = Math.min(id,destID) * 2000 + Math.max(id,destID);
												autoGen = "UID_U_" + destID + "_" + id + "_" + timeStamp + "_" + fileName;
												send(0,false,0,fileName,true,false);
												if(!fileMapping.containsKey(Integer.valueOf(chatID))) fileMapping.put(Integer.valueOf(chatID), new ArrayList<>());
												fileMapping.get(Integer.valueOf(chatID)).add(new StoreFile(fileName, autoGen));	
											}
										}
										if(autoGen != null) {
											int fileSizeCounter = 0;
											long fileSize = 0;
											File file = new File(autoGen);
											FileOutputStream fos = new FileOutputStream(file);
											byte[] dataBuffer = new byte[2048];
											i++;
											while(i < length) {
												if(fileSizeCounter < 8) {
													fileSize = fileSize + data[i] * (long)Math.pow(128, fileSizeCounter);
													fileSizeCounter++;
												}
												else {
													fos.write(data[i]);
													fileSize--;
													if(fileSize == 0) break;
												}
												i++;

											}
											while(fileSizeCounter < 8) {
												int value = inputStream.read();
												fileSize = fileSize + value * (long)Math.pow(128, fileSizeCounter);
												fileSizeCounter++;
											}
											while(fileSize > 0) {
												length = inputStream.read(dataBuffer);
												fos.write(dataBuffer,0,length);
												fileSize-=length;
											}
											lengthCounter = 0;
											senderCounter = 0;
											destCounter = 0;

											messLength = 0;
											senderID = 0;
											destID = 0;

											messBuffer = new StringBuilder();
											buffer = ByteBuffer.allocate(3000);
											status = 512;

											break;
										}
									}
									else if((status & SOUND) == SOUND) {
										if(voiceOver.get(Integer.valueOf(id)) != null) 
											voiceOver.get(Integer.valueOf(id)).sendVoice(buffer.array(), messLength);
									} else if ((status & DOWNLOAD) == DOWNLOAD) {
										try {
											int index = Integer.parseInt(messBuffer.toString());
											if((status & GROUP) == GROUP) {
												if(!groups.containsKey(Integer.valueOf(destID))) {
													send(0,false,0,"411 Invalid group ID",false,false);
												}
												else {
													if(groups.get(Integer.valueOf(destID)).usersInGroup.contains(this)) {
														Group group = groups.get(Integer.valueOf(destID));
														StoreFile sf = group.storeFile.get(index);
														send(0,false,0, sf.fileName,false,true);
														upload(sf.systemName);
														
													} else {
														send(0,false,0,"You havent joined this group yet", false, false);
													}
												}
											} else {
												int chatID = Math.min(id,destID) * 2000 + Math.max(id,destID);
												if(!fileMapping.containsKey(Integer.valueOf(chatID))) fileMapping.put(Integer.valueOf(chatID), new ArrayList<>());
												for(StoreFile sf : fileMapping.get(Integer.valueOf(chatID))) System.out.println(sf.systemName);
												StoreFile sf = fileMapping.get(Integer.valueOf(chatID)).get(index);
												send(0,false,0, sf.fileName,false,true);
												upload(sf.systemName);
												
											}
										}
										catch(Exception ex) {
											ex.printStackTrace();
											send(0,false,0,"Invalid file index",false,false);
										}
									}
								}


								lengthCounter = 0;
								senderCounter = 0;
								destCounter = 0;

								messLength = 0;
								senderID = 0;
								destID = 0;

								messBuffer = new StringBuilder();
								buffer = ByteBuffer.allocate(3000);
								status = 512;
							}
						}
					}	
				}
			}
			catch(Exception ex) {
				ex.printStackTrace();
			}	
			if(id != -1) {
				logout(id);
				for(Group group:groups.values()) group.leave(this);
				if(voiceOver.get(Integer.valueOf(id)) != null) {
					voiceOver.get(Integer.valueOf(id)).send(0,false,0,"Conversation ended",false, false);
					voiceOver.put(Integer.valueOf(voiceOver.get(Integer.valueOf(id)).id), null);
					voiceOver.put(Integer.valueOf(id), null);
				}
				for(SocketThread thread:users.values()) {
					int chatID = Math.min(id,thread.id) * 2000 + Math.max(id,thread.id);
					fileMapping.put(Integer.valueOf(chatID), null);
				}
				voiceInvt.put(Integer.valueOf(id), null);
				for(List list:voiceInvt.values()) 
					list.remove(Integer.valueOf(id));
			}
		}
	}

	static class Group {
		public int id;
		public List<SocketThread> usersInGroup;
		public List<StoreFile> storeFile;
		public Group(int id) {
			this.id = id;
			usersInGroup = new ArrayList<>();
			storeFile = new ArrayList<>();
		}

		public synchronized boolean join(SocketThread user) {
			if(usersInGroup.indexOf(user) != -1) return false;
			usersInGroup.add(user);
			return true;
		}

		public void broadcast(int sender, String message) {
			for(SocketThread user:usersInGroup) user.send(sender, true,id, message,false,false);
		}

		public synchronized void leave(SocketThread user) {
			usersInGroup.remove(user);
		}
	}

	static class Game {
		public int gameID;
		public boolean[][] firstPlayer;
		public boolean[][] secondPlayer;

		public boolean[][] firstShots;
		public boolean[][] secondShots;

		public int firstID;
		public int secondID;

		Game(int firstID, int secondID) {
			double firstMove = Math.random();
			if(firstMove >= 0.5) {
				this.firstID = firstID;
				this.secondID = secondID;
			}
			else {
				this.secondID = firstID;
				this.firstID = secondID;
			}

			firstPlayer = new boolean[10][10];
			secondPlayer = new boolean[10][10];
			
			firstShots = new boolean[10][10];
			secondShots = new boolean[10][10];

			for(int i=0;i<10;i++) for(int j=0;j<10;j++){
				firstPlayer[i][j] = false;
				secondPlayer[i][j] = false;
				firstShots[i][j] = false;
				secondShots[i][j] = false;
			}
		}

		public void setup(boolean[][] first, boolean[][] second) {

		}

		public void start() {
			
		}

	}
 
	public static void main(String[] args) {
		try {
			ServerSocket server = new ServerSocket(PORT);
			server.setReuseAddress(true);
			Socket client = null;

			while(true) {
				try {
					client = server.accept();
					SocketThread thread = new SocketThread(client);
					thread.start();
				}
				catch(Exception ex) {
					ex.printStackTrace();
				} 
			}
		}
		catch(Exception ex) {
			ex.printStackTrace();
		} 
	}
}

//04000000 00000000 01000000 01 68616c6f