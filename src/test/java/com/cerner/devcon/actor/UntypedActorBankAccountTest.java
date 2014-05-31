package com.cerner.devcon.actor;

import static akka.dispatch.Futures.*;
import static akka.pattern.Patterns.*;
import static org.junit.Assert.*;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import scala.concurrent.Await;
import scala.concurrent.ExecutionContext;
import scala.concurrent.Future;
import scala.concurrent.duration.Duration;
import scala.concurrent.duration.FiniteDuration;
import akka.actor.ActorRef;
import akka.actor.ActorSystem;
import akka.actor.Props;
import akka.actor.UntypedActor;
import akka.event.Logging;
import akka.event.LoggingAdapter;
import akka.testkit.JavaTestKit;
import akka.util.Timeout;

/**
 * Tests the UntypedActors
 * 
 */
public class UntypedActorBankAccountTest {

	private static final Logger log = LoggerFactory.getLogger(UntypedActorBankAccountTest.class);
	
	final FiniteDuration d = Duration.create(10, TimeUnit.SECONDS);
	final Timeout t = Timeout.durationToTimeout(d);

	private static final int taskCount = 100000;
	private static final int numTellers = 2;

	static ActorSystem system;

	@BeforeClass
	public static void setup() {
		system = ActorSystem.create();
	}

	@AfterClass
	public static void teardown() {
		JavaTestKit.shutdownActorSystem(system);
	}

	public static class BankTeller extends UntypedActor {

		LoggingAdapter log = Logging.getLogger(getContext().system(), self());

		private final int tellerTxfrs = taskCount / numTellers;

		private ActorRef accountA = getContext().actorOf(
				BankAccount.props(1, 0));
		private ActorRef accountB = getContext().actorOf(
				BankAccount.props(2, 0));

		private ActorRef probe;

		private List<ActorRef> txfrs = new ArrayList<ActorRef>();
		private int txfrCount = 0;

		public void onReceive(Object msg) {

			if (msg.equals("start")) {
				accountA.tell(new BankAccount.Deposit(taskCount * 100),
						getSelf());
				probe.tell("started", getSelf());

			} else if (msg.equals(BankAccount.TransactionStatus.DONE)) {
				log.info("deposit done");
				probe.tell("deposited", getSelf());
				for (int i = 0; i < tellerTxfrs / 2; i++) {
					ActorRef txfr = getContext().actorOf(
							Props.create(BankTransfer.class), "aToBtxfr" + i);

					ActorRef txfr2 = getContext().actorOf(
							Props.create(BankTransfer.class), "bToAtxfr" + i);
					txfrs.add(txfr);
					txfrs.add(txfr2);
					txfr.tell(new BankTransfer.Transfer(accountA, accountB, 2),
							getSelf());
					txfr2.tell(
							new BankTransfer.Transfer(accountB, accountA, 2),
							getSelf());
				}
			} else if (msg.equals(BankTransfer.TransferStatus.DONE)) {
				log.debug("txfr done");
				txfrs.remove(sender());
				txfrCount++;
				if (txfrCount % 100 == 0)
					log.info("Processed " + txfrCount + " txfrs");
				if (txfrCount == tellerTxfrs) {
					log.info("Processed all txfrs");
					probe.tell("done", getSelf());
				}
			} else if (msg.equals(BankTransfer.TransferStatus.FAILED)) {
				log.error("txfr failed");
			} else if (msg instanceof ActorRef) {
				probe = (ActorRef) msg;
			} else {
				log.error("unknown msg");
			}
		}

	}

	@Test
	public void testSimultaneousDeposit() throws Exception {

		new JavaTestKit(system) {
			{

				final ActorRef accountA = system.actorOf(BankAccount
						.props(1, 0));

				// the run() method needs to finish within 3 seconds
				new Within(duration("30 seconds")) {
					protected void run() {

						log.info("started deposits");
						final ActorRef testRef = getRef();

						final double depositAmt = 100;

						List<Future<Object>> futures = new ArrayList<Future<Object>>();
						for (int i = 0; i < taskCount; i++) {
							futures.add(ask(accountA, new BankAccount.Deposit(
									depositAmt), t));
						}

						try {
							awaitAll(futures);
							Future<Object> answer = ask(accountA,
									new BankAccount.BalanceRequest(), t);
							double balance = (Double) Await.result(answer, d);
							// Validate the number of exec tasks
							assertEquals(taskCount, futures.size());
							assertEquals(futures.size() * depositAmt, balance,
									1);
						} catch (Exception e) {
							e.printStackTrace();
							fail(e.getMessage());
						}

						log.info("finished deposits");
					}
				};
			}
		};
	}

	@Test
	public void testTransfer() {
		/*
		 * Wrap the whole test procedure within a testkit constructor if you
		 * want to receive actor replies or use Within(), etc.
		 */
		new JavaTestKit(system) {

			{
				final Props props = Props.create(BankTeller.class);

				final Map<ActorRef, JavaTestKit> tellers = new HashMap<ActorRef, JavaTestKit>();

				for (int i = 0; i < numTellers; i++) {
					final ActorRef teller = system.actorOf(props);

					// can also use JavaTestKit “from the outside”
					final JavaTestKit probe = new JavaTestKit(system);
					// “inject” the probe by passing it to the test subject
					// like a real resource would be passed in production
					teller.tell(probe.getRef(), getRef());
					tellers.put(teller, probe);
				}

				// the run() method needs to finish within 3 seconds
				new Within(duration("1 minutes")) {
					protected void run() {

						log.info("started txfrs");
						for (Map.Entry<ActorRef, JavaTestKit> entry : tellers
								.entrySet()) {
							ActorRef teller = entry.getKey();
							JavaTestKit probe = entry.getValue();
							teller.tell("start", getRef());
							probe.expectMsgEquals(duration("1 second"),
									"started");

							probe.expectMsgEquals(duration("1 second"),
									"deposited");

							probe.expectMsgEquals(duration("10 seconds"),
									"done");
						}

						log.info("Executed all txfrs");
						// Will wait for the rest of the 3 seconds
						// expectNoMsg();
					}
				};
			}
		};
	}

	private <T> void awaitAll(List<Future<T>> futures) throws Exception {
		final ExecutionContext ec = system.dispatcher();
		Await.result(sequence(futures, ec), d);

	}
}
