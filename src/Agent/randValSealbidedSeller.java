package Agent;

import jade.core.AID;
import jade.core.Agent;
import jade.core.behaviours.Behaviour;
import jade.core.behaviours.CyclicBehaviour;
import jade.core.behaviours.TickerBehaviour;
import jade.domain.DFService;
import jade.domain.FIPAAgentManagement.DFAgentDescription;
import jade.domain.FIPAAgentManagement.ServiceDescription;
import jade.domain.FIPAException;
import jade.lang.acl.ACLMessage;
import jade.lang.acl.MessageTemplate;
import jade.tools.introspector.gui.MyDialog;

import java.text.DecimalFormat;
import java.util.*;

public class randValSealbidedSeller extends Agent {
    randValSealbidedSellerGUI myGui;

    //General papameter information
    DecimalFormat df = new DecimalFormat("#.##");
    randValue randValue = new randValue();
    agentInfo sellerInfo = new agentInfo("", "seller", randValue.getRandDoubleRange(10,12), randValue.getRandDoubleRange(5000,13000), 0, 0.0, "", "looking");
    //double minSellingValue = sellerInfo.sellingVolumn * sellerInfo.sellingPrice;
    double minSellingValue = 0;
    //Instant papameter for AID[]
    //Create list of offer.
    HashMap<String, Double> sortHm = new HashMap<String, Double>();



    protected void setup(){
        // Create and show the GUI
        myGui = new randValSealbidedSellerGUI(this);
        myGui.show();
        //Start agent
        DFAgentDescription dfd = new DFAgentDescription();
        dfd.setName(getAID());
        ServiceDescription sd = new ServiceDescription();
        sd.setType("seller");
        sd.setName(getAID().getName());
        sellerInfo.farmerName = getAID().getName();
        dfd.addServices(sd);
        try {
            DFService.register(this, dfd);
        } catch (FIPAException fe) {
            fe.printStackTrace();
        }

        System.out.println(sellerInfo.farmerName + "  is ready" + "\n" + "Stage is  " + sellerInfo.agentType + "\n");

        //Add a TickerBehaviour that chooses agent status to buyer or seller.
        addBehaviour(new TickerBehaviour(this, 15000){
            protected void onTick() {
                myGui.displayUI("Name: " + sellerInfo.farmerName + "\n");
                myGui.displayUI("Status: " + sellerInfo.agentType + "\n");
                myGui.displayUI("Volumn to sell: " + sellerInfo.sellingVolumn + "\n");
                myGui.displayUI("Selling price: " + sellerInfo.sellingPrice + "\n");
                myGui.displayUI("\n");

                /*
                 ** Selling water process
                 */
                addBehaviour(new RequestPerformer());
                // Add the behaviour serving purchase orders from buyer agents
                //addBehaviour(new PurchaseOrdersServer());
            }
        } );
    }

    private class RequestPerformer extends Behaviour {
        //The list of known water selling agent
        private AID[] bidderAgent;
        private int repliesCnt; // The counter of replies from seller agents
        private MessageTemplate mt; // The template to receive replies
        ArrayList<String> bidderList = new ArrayList<String>();  //sorted list follows maximumprice factor.
        //ArrayList<combinatorialList> buyerList = new ArrayList<combinatorialList>();    //result list for selling process reference.

        //Creating dictionary for buyer volume and pricing
        Dictionary<String, Double> volumnDict = new Hashtable<String, Double>();
        Dictionary<String, Double> priceDict = new Hashtable<String, Double>();
        Object[] maxEuObj;
        Double maxEuValue = 0.0;
        ArrayList<String> maxEuList = new ArrayList<String>();
        private double waterVolFromBidder;
        private double biddedPriceFromBidder;
        private String acceptedName;
        private double acceptedValue;
        int countTick;
        int decisionRules = 0;

        private int step = 0;

        public void action() {
            switch (step) {
                case 0:
                    //update bidder list
                    DFAgentDescription template = new DFAgentDescription();
                    ServiceDescription sd = new ServiceDescription();
                    sd.setType("bidder");
                    template.addServices(sd);
                    try {
                        DFAgentDescription[] result = DFService.search(myAgent, template);
                        if(result.length > 1){
                            countTick = countTick+1;
                        }
                        myGui.displayUI("Found acutioneer agents:\n");
                        bidderAgent = new AID[result.length];
                        for (int i = 0; i < result.length; ++i) {
                            bidderAgent[i] = result[i].getName();
                            myGui.displayUI(bidderAgent[i].getName()+ "\n");
                        }
                    }
                    catch (FIPAException fe) {
                        fe.printStackTrace();
                    }
                    // Send the cfp to all sellers (Sending water volumn required to all bidding agent)
                    ACLMessage cfp = new ACLMessage(ACLMessage.CFP);
                    for (int i = 0; i < bidderAgent.length; ++i) {
                        if (bidderAgent[i].getName().equals(sellerInfo.farmerName)== false) {
                            cfp.addReceiver(bidderAgent[i]);
                        }
                    }
                    cfp.setContent(String.valueOf(Double.toString(sellerInfo.sellingVolumn) + "-"
                            + Double.toString((sellerInfo.sellingPrice))));
                    cfp.setConversationId("bidding");
                    cfp.setReplyWith("cfp"+System.currentTimeMillis()); // Unique value
                    myAgent.send(cfp);
                    //System.out.println("cfp message :" + "\n" + cfp);
                    // Prepare the template to get proposals
                    mt = MessageTemplate.and(MessageTemplate.MatchConversationId("bidding"),
                            MessageTemplate.MatchInReplyTo(cfp.getReplyWith()));
                    step = 1;
                    break;

                case 1:
                    // Receive all proposals/refusals from bidder agents
                    //Sorted all offers based on price Per mm.
                    ACLMessage reply = myAgent.receive(mt);
                    if (reply != null) {
                        repliesCnt++;
                        // Reply received
                        if (reply.getPerformative() == ACLMessage.PROPOSE) {
                            myGui.displayUI("Receive message: \n" + reply);
                            //Count number of bidder that is propose message for water price bidding.
                            // This is an offer
                            String biddedFromAcutioneer = reply.getContent();
                            String[] arrOfStr = biddedFromAcutioneer.split("-");
                            waterVolFromBidder = Double.parseDouble(arrOfStr[0]);
                            biddedPriceFromBidder = Double.parseDouble(arrOfStr[1]);
                            //adding data to dictionary
                            if(sellerInfo.sellingVolumn >= waterVolFromBidder && (waterVolFromBidder * biddedPriceFromBidder) > acceptedValue){
                                sellerInfo.acceptedVolumn = waterVolFromBidder;
                                sellerInfo.acceptedPrice = biddedPriceFromBidder;
                                 acceptedName = reply.getSender().getLocalName();
                                 acceptedValue  = waterVolFromBidder * biddedPriceFromBidder;
                            }
                        }

                        if (repliesCnt >= bidderAgent.length) {

                            // We received all replies
                            step = 2;
                        }
                    }else {
                        block();
                    }
                    break;
                case 2:
                    /*
                     * calulating and adding accepted water volumn for bidder based on highest price.
                     * Sendding message to bidders wiht two types (Accept proposal or Refuse) based on
                     * accepted water volumn to sell.
                     */
                    if(decisionRules == 0){
                        myGui.displayUI("\n" + "Best value is from :"+ acceptedName + "\n" + "Volumn to sell: " + sellerInfo.acceptedVolumn + "\n" + "Price per mm^3: " + sellerInfo.acceptedPrice);
                    }else if(decisionRules == 1) {
                        myGui.displayUI("\n" + "Best solution for each case:"+"\n"+"Max volumn selling:  " + Arrays.toString(maxEuObj)+ "\n");
                    }else if(decisionRules == 2){
                        myGui.displayUI("\n" + "Best solution for each case:"+"\n"+"Max profit loss protection:  " + Arrays.toString(maxEuObj)+ "\n");
                    }else{
                        myGui.displayUI("\n" + "Best solution for each case:"+"\n"+"balancing between volumn and price:  " + Arrays.toString(maxEuObj)+ "\n");
                    }
                    System.out.println("\n" + "Best solution for each case:"+"\n"+"Max price selling:  " + Arrays.toString(maxEuObj) + "\n");

                    for(int i=0; i < bidderAgent.length; ++i){
                            if(bidderAgent[i].getLocalName().equals(acceptedName)){
                                // Send the purchase order to the seller that provided the best offer
                                ACLMessage acceptedRequest = new ACLMessage(ACLMessage.ACCEPT_PROPOSAL);
                                acceptedRequest.addReceiver(bidderAgent[i]);
                                acceptedRequest.setConversationId("bidding");
                                acceptedRequest.setReplyWith("acceptedRequest" + System.currentTimeMillis());
                                //myGui.displayUI(acceptedRequest.toString());
                                myAgent.send(acceptedRequest);
                                myGui.displayUI("\n" + acceptedRequest.toString() + "\n");
                                mt = MessageTemplate.and(MessageTemplate.MatchConversationId("bidding"),MessageTemplate.MatchInReplyTo
                                        (acceptedRequest.getReplyWith()));
                            }else {
                                //Refuse message prepairing
                                ACLMessage rejectedRequest = new ACLMessage(ACLMessage.REFUSE);
                                rejectedRequest.addReceiver(bidderAgent[i]);
                                //myGui.displayUI(rejectedRequest.toString());
                                myAgent.send(rejectedRequest);
                                myGui.displayUI("\n" + rejectedRequest.toString() + "\n");
                            }
                    }

                    step = 3;
                    break;

                case 3:
                    // Receive the purchase order reply
                    reply = myAgent.receive(mt);
                    if (reply != null) {
                        double soldVolumn = 0;
                        //System.out.println("\n" + "Reply message:" + reply.toString());
                        //myGui.displayUI("\n" + "Reply message:" + reply.toString());
                        // Purchase order reply received
                        if (reply.getPerformative() == ACLMessage.INFORM) {
                            myGui.displayUI("Selling water to  " + reply.getSender().getLocalName());
                            sellerInfo.sellingVolumn = sellerInfo.sellingVolumn - soldVolumn;
                            System.out.println("Water volumn left :  " + sellerInfo.sellingVolumn);
                            // Purchase successful. We can terminate
                            //System.out.println(farmerInfo.farmerName +" successfully purchased from agent "+reply.getSender().getName() + "\n");
                            //System.out.println("Price = "+farmerInfo.currentPricePerMM);
                            //myGui.displayUI("\n" + farmerInfo.farmerName +" successfully purchased from agent "+reply.getSender().getName() +"\n");
                            //myGui.displayUI("Price = " + farmerInfo.currentPricePerMM);
                            myAgent.doSuspend();
                            //myAgent.doDelete();
                            //myGui.dispose();
                        }
                        else {
                            System.out.println("Attempt failed: requested water volumn already sold." + "\n");
                            //myGui.displayUI("Attempt failed: requested water volumn already sold." + "\n");
                        }
                        step = 4;
                    }
                    else {
                        block();
                    }
                    break;
            }
        }
        public boolean done() {
            if (step == 2 && acceptedName == null) {
                myGui.displayUI("Do not buyer who provide the matching price.");
                //myAgent.doSuspend();

                //myGui.dispose();
                //myGui.displayUI("Attempt failed: do not have bidder now" + "\n");
            }
            return step == 0;
            //return ((step == 2 && acceptedName == null) || step == 4) ;
        }
    }

    /*
     * 	PurchaseOrderServer
     * 	This behaviour is used by Seller agent to serve incoming offer acceptances (purchase orders) from buyer.
     * 	The seller agent will remove selling list and replies with an INFORM message to notify the buyer that purchase has been
     * 	successfully complete.
     */

    private class PurchaseOrdersServer extends CyclicBehaviour {
        public void action() {
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // ACCEPT_PROPOSAL Message received. Process it
                ACLMessage reply = msg.createReply();
                //myGui.displayUI(msg.toString());
                System.out.println(sellerInfo.sellingStatus);
                reply.setPerformative(ACLMessage.INFORM);
                if (sellerInfo.sellingStatus=="avalable") {
                    sellerInfo.sellingStatus = "sold";
                    //System.out.println(getAID().getName()+" sold water to agent "+msg.getSender().getName());
                    System.out.println(getAID().getLocalName()+" sold water to "+msg.getSender().getLocalName());
                    //myGui.displayUI(farmerInfo.sellingStatus.toString());
                    //System.out.println(farmerInfo.sellingStatus);
                    doSuspend();
                } else {
                    // The requested book has been sold to another buyer in the meanwhile.
                    reply.setPerformative(ACLMessage.FAILURE);
                    reply.setContent("not-available for sale");
                    //myGui.displayUI("not avalable to sell");
                }

            }else {
                block();
            }
        }
    }

    protected void takeDown() {
        try {
            DFService.deregister(this);
        }
        catch (FIPAException fe) {
            fe.printStackTrace();
        }
        // Printout a dismissal message
        System.out.println(getAID().getName()+" terminating.");
    }

    //All parameters and method for combinatorial auction process.
    public void powerSet(double set[]){
        int n = set.length;

        // Run a loop for printing all 2^n
        // subsets one by obe
        for (int i = 0; i < (1<<n); i++)
        {
            System.out.print("{ ");

            // Print current subset
            for (int j = 0; j < n; j++)

                // (1<<j) is a number with j th bit 1
                // so when we 'and' them with the
                // subset number we get which numbers
                // are present in the subset and which
                // are not
                if ((i & (1 << j)) > 0)
                    System.out.print(set[j] + " ");

            System.out.println("}");
        }
    }

    public void xorSum(int arr[], int n) {

        int bits = 0;

        // Finding bitwise OR of all elements
        for (int i = 0; i < n; ++i)
            bits |= arr[i];

        int ans = bits * (int)Math.pow(2, n-1);
    }

    static ArrayList<ArrayList<String> > getSubset(String[] set, int index) {
        ArrayList<ArrayList<String> > allSubsets;
        if (index < 0) {
            allSubsets = new ArrayList<ArrayList<String> >();
            allSubsets.add(new ArrayList<String>());
        }

        else {
            allSubsets = getSubset(set, index - 1);
            String item = set[index];
            ArrayList<ArrayList<String> > moreSubsets
                    = new ArrayList<ArrayList<String> >();

            for (ArrayList<String> subset : allSubsets) {
                ArrayList<String> newSubset = new ArrayList<String>();
                newSubset.addAll(subset);
                newSubset.add(item);
                moreSubsets.add(newSubset);
            }
            allSubsets.addAll(moreSubsets);
        }
        return allSubsets;
    }

    // Function to convert ArrayList<String> to String[]
    public static String[] GetStringArray(ArrayList<String> arr)
    {
        // declaration and initialise String Array
        String str[] = new String[arr.size()];
        // ArrayList to Array Conversion
        for (int j = 0; j < arr.size(); j++) {
            // Assign each value to String array
            str[j] = arr.get(j);
        }
        return str;
    }


    // function to sort hashmap by values
    public static HashMap<String, Double> sortByValue(HashMap<String, Double> hm)
    {
        // Create a list from elements of HashMap
        List<Map.Entry<String, Double> > list =
                new LinkedList<Map.Entry<String, Double> >(hm.entrySet());

        // Sort the list
        Collections.sort(list, new Comparator<Map.Entry<String, Double> >() {
            public int compare(Map.Entry<String, Double> o1,
                               Map.Entry<String, Double> o2)
            {
                return (o1.getValue()).compareTo(o2.getValue());
            }
        });

        // put data from sorted list to hashmap
        HashMap<String, Double> temp = new LinkedHashMap<String, Double>();
        for (Map.Entry<String, Double> aa : list) {
            temp.put(aa.getKey(), aa.getValue());
        }
        return temp;
    }


    public class agentInfo{
        String farmerName;
        String agentType;
        Double sellingPrice;
        Double sellingVolumn;
        Double acceptedPrice;
        Double acceptedVolumn;
        String acceptedName;
        String sellingStatus;

        agentInfo(String farmerName, String agentType, double sellingPrice, double sellingVolumn, double acceptedPrice, double acceptedVolumn, String acceptedName, String sellingStatus){
            this.farmerName = farmerName;
            this.agentType = agentType;
            this.sellingPrice = sellingPrice;
            this.sellingVolumn = sellingVolumn;
            this.acceptedPrice = acceptedPrice;
            this.acceptedVolumn = acceptedVolumn;
            this.acceptedName = acceptedName;
            this.sellingStatus = sellingStatus;

        }
    }
}

/***
    //Advertise service reply
    private class AdvertiseServiceReply extends CyclicBehaviour{
        public void action(){
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.CFP);
            ACLMessage msg = myAgent.receive(mt);
            if (msg != null) {
                // CFP Message received. Process it
                String title = msg.getContent();
                ACLMessage reply = msg.createReply();
                reply.setPerformative(ACLMessage.PROPOSE);
                reply.setContent(sellerInfo.sellingVolumn + "-" + sellerInfo.sellingPrice + "-" + sellerInfo.agentType);
                myAgent.send(reply);
            }
            else {
                block();
            }
        }
    }

    private class PrioritizedOffers extends CyclicBehaviour{
        public void action(){
            MessageTemplate mt = MessageTemplate.MatchPerformative(ACLMessage.ACCEPT_PROPOSAL);
            ACLMessage msg = myAgent.receive(mt);
            if(msg != null){
                myGui.displayUI(msg.toString());
                String[] arrOfStr = msg.getContent().split("-");
                double tempVolumn = Double.parseDouble(arrOfStr[0]);
                double tempPrice = Double.parseDouble(arrOfStr[1]);
                String tempAgentStatus = arrOfStr[2];
                double tempValue = tempPrice * tempVolumn;
                if(tempValue > acceptedValue){
                    sellerInfo.sellingVolumn = tempVolumn;
                    sellerInfo.acceptedPrice = tempPrice;
                    acceptedBidder = msg.getSender();
                    myGui.displayUI(msg.getSender().getLocalName() + " proposed the highest Value offer now" + "\n");
                }
            }else {
                myGui.displayUI("null \n");
                block();
            }
        }
    }

    private class PurchaseOrdersServer extends CyclicBehaviour{
        public void action(){
            //Sending Inform and failure message to others.
            if(acceptedBidder != null){
                myGui.displayUI("xxxxxxxxxxxxxxxxxxxxxxxxxxx");
                for(int i = 0; i < agentList.length; i++){
                    if(agentList[i].getName().equals(acceptedBidder.getName()) == true){
                        ACLMessage reply = new ACLMessage(ACLMessage.INFORM);
                        reply.setContent("Accept");
                        myAgent.send(reply);
                    }else {
                        ACLMessage reply = new ACLMessage(ACLMessage.FAILURE);
                        reply.setContent("Reject");
                        myAgent.send(reply);
                    }
                }
            }else {
                myGui.displayUI("accepted Agent is null \n");
                block();
            }
        }
    }

***/

