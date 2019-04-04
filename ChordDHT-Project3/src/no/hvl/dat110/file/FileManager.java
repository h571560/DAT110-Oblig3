package no.hvl.dat110.file;

import java.io.BufferedWriter;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.math.BigInteger;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
//import java.util.ArrayList;
//import java.util.Comparator;
import java.util.HashSet;
//import java.util.List;
import java.util.Set;

import no.hvl.dat110.node.Message;
//import no.hvl.dat110.node.OperationType;
import no.hvl.dat110.node.Operations;
import no.hvl.dat110.rpc.interfaces.ChordNodeInterface;
import no.hvl.dat110.util.Hash;
//import no.hvl.dat110.util.Util;

public class FileManager extends Thread {
	
	private BigInteger[] replicafiles;					// array stores replicated files for distribution to matching nodes
	private int nfiles;								// let's assume each node manages nfiles (5 for now) - can be changed from the constructor
	private ChordNodeInterface chordnode;
	
	public FileManager(ChordNodeInterface chordnode, int N) throws RemoteException {
		this.nfiles = N;
		replicafiles = new BigInteger[N];
		this.chordnode = chordnode;
	}
	
	public void run() {
		
		while(true) {
			try {
				distributeReplicaFiles();
				Thread.sleep(3000);
			} catch (InterruptedException | IOException e) {
				e.printStackTrace();
			}
		}
	}
	
	public void createReplicaFiles(String filename) {
		
		for(int i=0; i<nfiles; i++) {
			String replicafile = filename + i;
			replicafiles[i] = Hash.hashOf(replicafile);	

		}
		//System.out.println("Generated replica file keyids for "+chordnode.getNodeIP()+" => "+Arrays.asList(replicafiles));
	}
	
	private void distributeReplicaFiles() throws IOException {
		
		// lookup(keyid) operation for each replica
		// findSuccessor() function should be invoked to find the node with identifier id >= keyid and store the file (create & write the file)
		
		for(int i=0; i<replicafiles.length; i++) {
			BigInteger fileID = replicafiles[i];
			ChordNodeInterface succOfFileID = chordnode.findSuccessor(fileID);
			
			// if we find the successor node of fileID, we can assign the file to the successor. This should always work even with one node
			if(succOfFileID != null) {
				succOfFileID.addToFileKey(fileID);
				String initialcontent = chordnode.getNodeIP()+"\n"+chordnode.getNodeID();
				succOfFileID.createFileInNodeLocalDirectory(initialcontent, fileID);			// copy the file to the successor local dir
			}			
		}
	}
	
	/**
	 * 
	 * @param filename name of file
	 * @return list of active nodes in a list of messages having the replicas of this file
	 * @throws RemoteException throws exception
	 */
	public Set<Message> requestActiveNodesForFile(String filename) throws RemoteException {
		
		// generate the N replica keyids from the filename
		Set<Message> activeNodesForFile = new HashSet<>();

		// create replicas
		createReplicaFiles(filename);
		
		// findsuccessors for each file replica and save the result (fileID) for each successor
		for(int i=0;i<replicafiles.length;i++){
			BigInteger fileID = replicafiles[i];
			ChordNodeInterface succesorOfFileID = chordnode.findSuccessor(fileID);

			if(succesorOfFileID != null){
				Message m = succesorOfFileID.getFilesMetadata().get(fileID);
				if(!checkDuplicateActiveNode(activeNodesForFile,m)){
					activeNodesForFile.add(m);
				}
			}
		}
		// if we find the successor node of fileID, we can retrieve the message associated with a fileID by calling the getFilesMetadata() of chordnode.
		
		// save the message in a list but eliminate duplicated entries. e.g a node may be repeated because it maps more than one replicas to its id. (use checkDuplicateActiveNode)
		
		return activeNodesForFile;	// return value is a Set of type Message
	}
	
	private boolean checkDuplicateActiveNode(Set<Message> activenodesdata, Message nodetocheck) {
		
		for(Message nodedata : activenodesdata) {
			if(nodetocheck.getNodeID().compareTo(nodedata.getNodeID()) == 0)
				return true;
		}
		
		return false;
	}
	
	public boolean requestToReadFileFromAnyActiveNode(String filename) throws RemoteException, NotBoundException {
		boolean request = false;
		// get all the activenodes that have the file (replicas) i.e. requestActiveNodesForFile(String filename)
		Set<Message> nodes = requestActiveNodesForFile(filename);
		Message message = null;
		// choose any available node
		if(!nodes.isEmpty()) {
			message = nodes.iterator().next();
		}
		ChordNodeInterface node = null;
		// locate the registry and see if the node is still active by retrieving its remote object
		if(message != null) {
			Registry reg = LocateRegistry.getRegistry(message.getNodeIP());
			node = (ChordNodeInterface) reg.lookup(message.getNodeID().toString());

			// build the operation to be performed - Read and request for votes in existing active node message
			request = node.requestReadOperation(message);
		}
		// set the active nodes holding replica files in the contact node (setActiveNodesForFile)
 		
		// set the NodeIP in the message (replace ip with )
		
		
		// send a request to a node and get the voters decision
		
		// put the decision back in the message
		
		// multicast voters' decision to the rest of the nodes
		
		// if majority votes
		
		// acquire lock to CS and also increments localclock
		
		// perform operation by calling Operations class
		
		// optional: retrieve content of file on local resource
		
		// send message to let replicas release read lock they are holding

		if(request) {
			try {
				node.multicastVotersDecision(message);
				node.acquireLock();
				try {
					sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				Operations operation = new Operations(node, message, nodes);
				operation.performOperation();
				try{
					distributeReplicaFiles();
				}catch (Exception e){
					e.printStackTrace();
				}
				// send message to let replicas release read lock they are holding
				node.multicastUpdateOrReadReleaseLockOperation(message);
				// release locks after operations
				node.releaseLocks();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
			
		return request;		// change to your final answer
	}
	
	public boolean requestWriteToFileFromAnyActiveNode(String filename, String newcontent) throws RemoteException, NotBoundException {

		boolean request = false;
		// get all the activenodes that have the file (replicas) i.e. requestActiveNodesForFile(String filename)
		Set<Message> nodes = requestActiveNodesForFile(filename);
		Message message = null;
		// choose any available node
		if(!nodes.isEmpty()) {
			message = nodes.iterator().next();
		}
		ChordNodeInterface node = null;
		// locate the registry and see if the node is still active by retrieving its remote object
		if(message != null) {
			Registry reg = LocateRegistry.getRegistry(message.getNodeIP());
			node = (ChordNodeInterface) reg.lookup(message.getNodeID().toString());

			// build the operation to be performed - Read and request for votes in existing active node message
			request = node.requestReadOperation(message);
		}
		
		// get all the activenodes that have the file (replicas) i.e. requestActiveNodesForFile(String filename)
		
		// choose any available node
		
		// locate the registry and see if the node is still active by retrieving its remote object
		
		// build the operation to be performed - Read and request for votes in existing active node message
		
		// set the active nodes holding replica files in the contact node (setActiveNodesForFile)
 		
		// set the NodeIP in the message (replace ip with )

		if(request) {
			try {
				node.multicastVotersDecision(message);
				node.acquireLock();
				try {
					sleep(1000);
				} catch (InterruptedException e) {
					e.printStackTrace();
				}
				Operations operation = new Operations(node, message, nodes);
				operation.performOperation();
				node.multicastUpdateOrReadReleaseLockOperation(message);
				node.releaseLocks();
			} catch (IOException e) {
				e.printStackTrace();
			}

		}
		
		// send a request to a node and get the voters decision
		
		// put the decision back in the message
		
		// multicast voters' decision to the rest of the nodes
		
		// if majority votes
		
		// acquire lock to CS and also increments localclock
		
		// perform operation by calling Operations class
		
		// update replicas and let replicas release CS lock they are holding
		
		// release locks after operations
		
		return false;  // change to your final answer

	}

	/**
	 * create the localfile with the node's name and id as content of the file
	 * @throws RemoteException  throws a remote exception
	 */
	public void createLocalFile() throws RemoteException {
		String nodename = chordnode.getNodeIP();
		String path = new File(".").getAbsolutePath().replace(".", "");
		File fpath = new File(path+"/"+nodename);				// we'll have ../../nodename/
		if(!fpath.exists()) {
			boolean suc = fpath.mkdir();
			try {
				if(suc) {
					File file = new File(fpath+"/"+nodename); 	// end up with:  ../../nodename/nodename  (actual file no ext)
					file.createNewFile();	
					// write the node's data into this file
					writetofile(file);
				}
			} catch (IOException e) {
				
				//e.printStackTrace();
			}
		}
		
	}
	
	private void writetofile(File file) throws RemoteException {
		
		try {
			BufferedWriter bw = new BufferedWriter(new FileWriter(file, true));
			bw.write(chordnode.getNodeIP());
			bw.newLine();
			bw.write(chordnode.getNodeID().toString());
			bw.close();
									
		} catch (IOException e) {
			
			//e.printStackTrace();
		}
	}
}
