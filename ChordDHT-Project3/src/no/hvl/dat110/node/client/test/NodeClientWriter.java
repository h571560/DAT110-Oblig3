package no.hvl.dat110.node.client.test;

/**
 * exercise/demo purpose in dat110
 * @author tdoy
 *
 */

import java.math.BigInteger;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.Registry;
import java.util.List;

import no.hvl.dat110.file.FileManager;
import no.hvl.dat110.node.Message;
import no.hvl.dat110.rpc.StaticTracker;
import no.hvl.dat110.rpc.interfaces.ChordNodeInterface;
import no.hvl.dat110.util.Hash;
import no.hvl.dat110.util.Util;

public class NodeClientWriter extends Thread {

	private boolean succeed = false;
	private String content;
	private String filename;
	
	public NodeClientWriter(String content, String filename) {
		this.content = content;
		this.filename = filename;
	}
	
	public void run() {
		sendRequest();
	}
	
	private void sendRequest() {

		// Lookup(key) - Use this class as a client that is requesting for a new file and needs the identifier and IP of the node where the file is located
		// assume you have a list of nodes in the tracker class and select one randomly. We can use the Tracker class for this purpose
		String activeNode = StaticTracker.ACTIVENODES[0];

		// connect to an active chord node - can use the process defined in StaticTracker
		BigInteger id = Hash.hashOf(activeNode);
		// Compute the hash of the node's IP address
		// use the hash to retrieve the ChordNodeInterface remote object from the registry
		try {
			ChordNodeInterface node = (ChordNodeInterface) Util.locateRegistry(activeNode).lookup((id.toString()));
			FileManager fm = new FileManager(node,StaticTracker.N);
			succeed = fm.requestWriteToFileFromAnyActiveNode(activeNode,filename);
		} catch (RemoteException e) {
			succeed = false;
			e.printStackTrace();
		} catch (NotBoundException e) {
			succeed = false;
			e.printStackTrace();
		}
		
	}
	
	public boolean isSucceed() {
		return succeed;
	}

	public void setSucceed(boolean succeed) {
		this.succeed = succeed;
	}

}
