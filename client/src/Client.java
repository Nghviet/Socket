/*

	Nguyen Hoang Viet _ 18020063
	Client

*/
import java.io.*;
import java.nio.*;
import java.net.*;
import java.util.regex.*;
import java.math.BigInteger;
import java.lang.Thread;

import javax.sound.sampled.*;

public class Client {

	static int SERVER_PORT = 9000;
	static byte SOUND = 16;
	static byte GROUP = 8;
	static byte UPLOAD = 4;
	static byte DOWNLOAD = 2;
	static byte MESSAGE = 1;

	static boolean voiceAck = false;
	static private Socket server;
	static private Input input = new Input();
	static private VoiceInput voiceInput = new VoiceInput();
	public static void send(String message) {

	}

	public static AudioFormat format = new AudioFormat(8000, 8, 2, true, true);

	public static synchronized boolean upload(String fileName) {
		try {
			DataOutputStream writer = new DataOutputStream(server.getOutputStream());
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



	public static synchronized boolean send(int destID, boolean group, String message, boolean voice) {
		int status = 0;
		if(group) status = status | GROUP;
		if(voice) status = status | SOUND;
		else
		if(message.startsWith("UPLOAD ")) {
			status = status | UPLOAD;
			message = message.substring(7);
			File f = new File(message);
			if(!f.exists() || f.isDirectory()) {
				System.out.println("File not available");
				return false;
			}
		} else 
		if(message.startsWith("DOWNLOAD ")) {
			status = status | DOWNLOAD;
			message = message.substring(9);
		}
		else status = status | MESSAGE;

		int length = message.length();
		byte[] mess = message.getBytes();

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

		data[8] = (byte)(destID % 128);
		destID /= 128;
		data[9] = (byte)(destID % 128);
		destID /= 128;
		data[10] = (byte)(destID % 128);
		destID /= 128;
		data[11] = (byte)(destID % 128);
		destID /= 128;

		data[12] = (byte)status;
		for(int i=0;i<message.length();i++) data[i+13] = mess[i];
		try {
			DataOutputStream writer = new DataOutputStream(server.getOutputStream());
			writer.write(data);
			writer.flush();
		}
		catch(Exception ex) {
			ex.printStackTrace();
			return false;
		}

		return true;
	}

	public static synchronized boolean send(int destID, boolean group, String message) {
		return send(destID, group, message, false);
	}

	public static synchronized boolean sendVoice(byte[] voice,int length) {
		int status = 0;
		status = status | SOUND;
	
		byte[] mess = voice;

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

		for(int i=0;i<mess.length;i++) data[i+13] = mess[i];
		try {
			DataOutputStream writer = new DataOutputStream(server.getOutputStream());
			writer.write(data);
		}
		catch(Exception ex) {
			ex.printStackTrace();
			return false;
		}

		return true;
	}

	static class Input extends Thread {
		Input() {

		}

		public void run() {
			String pattern = "^([SG]) ([0-9]*) (.*)$";
			Pattern inputPattern = Pattern.compile(pattern);
			try {
				while(true) {
					Console console = System.console();
					String in = console.readLine("Client : ");
					Matcher matcher = inputPattern.matcher(in);
					if(!matcher.matches()) {
						System.out.println("Input err");
						continue;
					}
					send(Integer.parseInt(matcher.group(2)),matcher.group(1).equals("G"),matcher.group(3));
				}
				
			}
			catch(Exception ex) {
				ex.printStackTrace();
			}

		}
	}

	static class VoiceInput extends Thread {
		VoiceInput() {

		}

		public void run() {	
			try {
				System.out.println("Mic started");
				TargetDataLine line;

				DataLine.Info info = new DataLine.Info(TargetDataLine.class, 
				    format); // format is an AudioFormat object
				DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);
				if (!AudioSystem.isLineSupported(info)) {
				    // Handle the error ... 

				}
				SourceDataLine soundLine = (SourceDataLine) AudioSystem.getLine(sourceInfo);
				line = (TargetDataLine) AudioSystem.getLine(info);
				line.open(format, 2500);
				//soundLine.open(format,2500);
				line.start();
				//soundLine.start();
				byte[] data = new byte[2500];
				while(true) {
					int numBytesRead =  line.read(data, 0, data.length);
					sendVoice(data,numBytesRead);
				//	soundLine.write(data, 0, numBytesRead);
				//	soundLine.drain();
				}
			}
			catch(Exception ex) {
				ex.printStackTrace();
			}
		  	
		}
	}	


	public static void main(String[] args) {
		try {
			server = new Socket(InetAddress.getLocalHost(),SERVER_PORT);
			int lengthCounter = 0;
			int senderCounter = 0;
			int destCounter = 0;

			int messLength = 0;
			int senderID = 0;
			int destID = 0;
		
			int status = 512;
			int id = -1;

			StringBuilder messBuffer = new StringBuilder();
			ByteBuffer buffer = ByteBuffer.allocate(3000);
			InputStream inputStream = server.getInputStream();
			DataLine.Info sourceInfo = new DataLine.Info(SourceDataLine.class, format);
			SourceDataLine soundLine = (SourceDataLine) AudioSystem.getLine(sourceInfo);
			soundLine.open(format,2500);
        	

			while(id == -1) {
				Console console = System.console();
				String in = console.readLine("UserID : ");
				send(0,false,in);
				try {
					id = Integer.parseInt(in);
				}
				catch(Exception ex) {
					ex.printStackTrace();
					continue;
				}
				byte[] data = new byte[2048];
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
							if(messBuffer.length() == messLength) {
								System.out.println("Message from " + senderID + ": " + messBuffer);
								if(!messBuffer.toString().equals("210 Authenticated")) {
									id = -1;
								}

								lengthCounter = 0;
								senderCounter = 0;
								destCounter = 0;

								messLength = 0;
								senderID = 0;
								destID = 0;

								messBuffer = new StringBuilder();

								status = 512;
								break;
							}
						}
					}
					if(lengthCounter == 0) break;
				}
			}	

			input.start();

			while(true) {
				byte[] data = new byte[3000];
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
						buffer.put(data[i]);
						if(messBuffer.length() == messLength) {
							if((status & MESSAGE) == MESSAGE) {
								if(destID == 0)	System.out.println("Message from " + senderID + ": " + messBuffer);
								else System.out.println("Message from " + senderID + " in group " + destID + " : " + messBuffer);
								if(messBuffer.toString().equals("Conversation ended")){
									voiceInput.stop();
									soundLine.stop();
								}
								if(messBuffer.toString().equals("250 Voice start")) {
									voiceInput.start();
									soundLine.start();
								}
							}
							if((status & UPLOAD) == UPLOAD) {
								upload(messBuffer.toString());
							}

							if((status & DOWNLOAD) == DOWNLOAD) {
								String fileName = messBuffer.toString();
								System.out.println("DOWNLOAD " + fileName);
								i++;
								File file = new File(fileName);
								FileOutputStream fos = new FileOutputStream(file);

								int fileLengthCounter = 0;
								long fileLength = 0;
								while(i<length) {
									if(fileLengthCounter < 8) {
										fileLength = fileLength + (long)Math.pow(data[i],fileLengthCounter);
										i++;
										fileLengthCounter++;
									}
									else {
										fos.write(data[i]);
										fileLength--;
										if(fileLength == 0) break;
										i++;
									}
								}

								while(fileLengthCounter < 8) {
									int value = inputStream.read();
									fileLength = fileLength + value * (long)Math.pow(128, fileLengthCounter);
									fileLengthCounter++;
								}
								byte[] dataBuffer = new byte[2048];
								while(fileLength > 0) {
									length = inputStream.read(dataBuffer);
									fos.write(dataBuffer,0,length);
									fileLength-=length;
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

							if((status & SOUND) == SOUND) {
								char[] str = messBuffer.toString().toCharArray();
								byte[] soundData = new byte[str.length];
								for(int j=0;j<str.length;j++) soundData[j] = (byte) str[j];
								soundLine.write(buffer.array(),0,messLength - messLength % 2);
								soundLine.drain();
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
	}

}