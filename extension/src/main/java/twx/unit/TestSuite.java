package twx.unit;

import com.thingworx.entities.utils.ThingUtilities;
import com.thingworx.metadata.ServiceDefinition;
import com.thingworx.metadata.collections.ServiceDefinitionCollection;
import com.thingworx.things.Thing;
import com.thingworx.types.BaseTypes;
import com.thingworx.types.InfoTable;
import com.thingworx.types.collections.ValueCollection;
import com.thingworx.types.primitives.InfoTablePrimitive;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

public class TestSuite {

    private final String name;
    private final List<TestSuite> suites = new ArrayList<>();
    private final List<TestDefinition> testCases = new ArrayList<>();
    private final TwxUnitExecutor executor; // TODO: Remove

    public TestSuite(TwxUnitExecutor executor, String thing, String parent, String runAsDefault, Set<String> parsed) throws TestingException {
        this.executor = executor;
        parsed.add(thing);
        this.name = thing;
        Thing t = ThingUtilities.findThing(thing);
        if (t == null) {
            throw new TestingException("Test suite " + thing + (parent == null ? "" : (", referenced in " + parent)) + " does not exist");
        }
        if (t.implementsShape("HasTestCases")) {
            parseAuto(t, runAsDefault, parsed);
        } else if (t.implementsShape("HasTestSuite")) {
            parseManual(t, runAsDefault, parsed);
        } else {
            throw new TestingException("Thing " + thing + (parent == null ? "" : (", referenced in " + parent)) + " is not a test suite");
        }
    }

    private void parseManual(Thing thing, String runAsDefault, Set<String> parsed) throws TestingException {
        InfoTable def;
        try {
            def = ((InfoTablePrimitive) thing.getPropertyValue("testSuite")).getValue();
        } catch (Exception e) {
            e.printStackTrace();
            throw new TestingException("Unable to read property testSuite on thing " + thing.getName() + ": " + e.getMessage(), e);
        }

        for (int i = 0; i < def.getLength(); ++i) {
            ValueCollection test = def.getRow(i);
            String testSuite = test.getStringValue("testSuite");
            String testCase = test.getStringValue("testCase");
            if ((testSuite == null || testSuite.isEmpty()) && (testCase == null || testCase.isEmpty())) {
                throw new TestingException("Invalid test suite configuration: both testCase and testSuite cannot be null or empty simultaneously");
            }

            String runAs = test.getStringValue("runAs");
            if (runAs == null || runAs.isEmpty()) {
                runAs = runAsDefault;
            }
            if (testCase == null || testCase.isEmpty()) {
                // Reference to another test suite
                if (parsed.contains(testSuite)) {
                    throw new TestingException("Detected an infinite loop on test suite " + testSuite + ", path: " + parsed);
                }
                suites.add(new TestSuite(executor, testSuite, thing.getName(), runAs, parsed));
            } else {
                // Reference to test case
                if (testSuite == null || testSuite.isEmpty()) {
                    testSuite = thing.getName();
                }
                testCases.add(new TestDefinition(testSuite, testCase, runAs));
            }
        }
    }

    private void parseAuto(Thing thing, String runAsDefault, Set<String> parsed) throws TestingException {
        ServiceDefinitionCollection services;
        try {
            services = thing.getInstanceServiceDefinitions();
        } catch (Exception e) {
            e.printStackTrace();
            throw new TestingException("Unable to get service definitions on thing " + thing.getName() + ": " + e.getMessage(), e);
        }
        for (ServiceDefinition service: services.getOrderedList() ) {
            String name = service.getName();
            String category = service.getCategory();
            String description = service.getDescription();

            if( category.toLowerCase().startsWith("test") || name.toLowerCase().startsWith("test") ) {
                if( service.getResultType().getBaseType() != BaseTypes.NOTHING ) {
                    throw new TestingException("Test case " + thing.getName() + "." + name + " must return NOTHING");
                } else if (service.getParameters().size() > 0 ) {
                    throw new TestingException("Test case " + thing.getName() + "." + name + " must not declare any inputs");
                }
                testCases.add(new TestDefinition(thing.getName(), name, runAsDefault, description));
            }
        }
    }

    public String getName() {
        return name;
    }

    public List<TestSuite> getSuites() {
        return suites;
    }

    public List<TestDefinition> getTestCases() {
        return testCases;
    }
}
