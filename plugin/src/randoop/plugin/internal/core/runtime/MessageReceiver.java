package randoop.plugin.internal.core.runtime;

import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.net.ServerSocket;
import java.net.Socket;

import randoop.runtime.IMessage;
import randoop.runtime.RandoopFinished;

public class MessageReceiver implements Runnable {
  private IMessageListener fIMessageListener;
  private ServerSocket fServerSocket;

  /**
   * 
   * @param messageListener
   * @throws IOException
   *           if unable to create socket
   */
  public MessageReceiver(IMessageListener messageListener) throws IOException {
    if (messageListener == null) {
      fIMessageListener = new NullMessageListener();
    } else {
      fIMessageListener = messageListener;
    }
    
    fServerSocket = new ServerSocket(0);
    assert fServerSocket.isBound();
  }

  public int getPort() {
    return fServerSocket.getLocalPort();
  }

  @Override
  public void run() {
    try {
      Socket sock = fServerSocket.accept();
      InputStream iStream = sock.getInputStream();
      ObjectInputStream objectInputStream = new ObjectInputStream(iStream);

      IMessage start = (IMessage) objectInputStream.readObject();
      fIMessageListener.handleMessage(start);
      
      IMessage work = null;
      do {
        work = (IMessage) objectInputStream.readObject();
        
        fIMessageListener.handleMessage(work);
      } while (work != null && !(work instanceof RandoopFinished));
    } catch (IOException ioe) {
      // Stream terminated unexpectedly
      fIMessageListener.handleTermination();
    } catch (ClassNotFoundException e) {
      System.err.println("Incorrect class " + e);
    } finally {
      try {
        fServerSocket.close();
      } catch (IOException ioe) {
      }
    }
  }
}