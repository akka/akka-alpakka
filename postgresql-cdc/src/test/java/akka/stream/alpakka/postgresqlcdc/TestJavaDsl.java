/*
 * Copyright (C) 2016-2018 Lightbend Inc. <http://www.lightbend.com>
 */

package akka.stream.alpakka.postgresqlcdc;


import akka.NotUsed;
import akka.actor.ActorSystem;
import akka.stream.ActorMaterializer;
import akka.stream.Materializer;
import akka.stream.alpakka.postgresqlcdc.javadsl.ChangeDataCapture;
import akka.stream.javadsl.Source;
import akka.stream.testkit.javadsl.TestSink;
import akka.testkit.javadsl.TestKit;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.sql.Connection;
import java.util.ArrayList;

public class TestJavaDsl {

    private static ActorSystem system;
    private static Materializer materializer;
    private static Connection connection;

    private static final String connectionString = "jdbc:postgresql://localhost:5435/pgdb2?user=pguser&password=pguser";

    @BeforeClass
    public static void setup() throws Exception {
        system = ActorSystem.create();
        materializer = ActorMaterializer.create(system);
        String driver = "org.postgresql.Driver";
        Class.forName(driver).newInstance();
        connection = FakeDb.getConnection(connectionString);
        // set the logical replication slot
        FakeDb.setUpLogicalDecodingSlot("junit", connection);
        FakeDb.createCustomersTable(connection);
    }

    @AfterClass
    public static void teardown() throws Exception {
        TestKit.shutdownActorSystem(system);
        system = null;
        materializer = null;
        FakeDb.dropTableCustomers(connection);
        // drop the logical replication slot
        FakeDb.dropLogicalDecodingSlot("junit", connection);
        connection.close();
    }

    @Test
    public void testIt() {

        // some inserts
        FakeDb.insertCustomer(0, "John", "Lennon", "john.lennon@akka.io", new ArrayList(), connection);
        // some updates
        FakeDb.updateCustomerEmail(0, "john.lennon@thebeatles.com", connection);
        // some deletes
        FakeDb.deleteCustomers(connection);

        final PostgreSQLInstance postgreSQLInstance = PostgreSQLInstance
                .create(connectionString, "junit");

        final Source<Change, NotUsed> source = ChangeDataCapture
                .source(postgreSQLInstance)
                .mapConcat(ChangeSet::getChanges);

        source.runWith(TestSink.probe(system), materializer)
                .request(3)
                .expectNextN(3); // TODO: improve test

    }

}

