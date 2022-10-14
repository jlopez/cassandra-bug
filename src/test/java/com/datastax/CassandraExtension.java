package com.datastax;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.net.InetSocketAddress;
import java.util.function.Predicate;
import java.util.regex.Pattern;

import com.datastax.oss.driver.api.core.CqlSession;
import lombok.val;
import org.junit.jupiter.api.extension.*;
import org.junit.platform.commons.util.ExceptionUtils;
import org.junit.platform.commons.util.ReflectionUtils;
import org.testcontainers.containers.CassandraContainer;
import org.testcontainers.utility.DockerImageName;

import static org.junit.platform.commons.util.AnnotationUtils.findAnnotatedFields;
import static org.junit.platform.commons.util.ReflectionUtils.makeAccessible;

public class CassandraExtension implements BeforeAllCallback, BeforeEachCallback, AfterAllCallback, ParameterResolver {
    private CassandraContainer<?> cassandraContainer;

    private CqlSession createSession() {
        if (shouldUseRemoteDatabase())
            return createRemoteSession();
        else
            return createTestContainerSession();
    }

    private boolean shouldUseRemoteDatabase() {
        return System.getenv().containsKey("CASSANDRA_URL");
    }

    private CqlSession createTestContainerSession() {
        return CqlSession.builder()
                .addContactPoint(getCassandraContainer().getContactPoint())
                .withLocalDatacenter(getCassandraContainer().getLocalDatacenter())
                .withKeyspace("ks")
                .build();
    }

    synchronized private CassandraContainer<?> getCassandraContainer() {
        if (cassandraContainer == null) {
            cassandraContainer = new CassandraContainer<>(DockerImageName.parse("cassandra:4"))
                    .withInitScript("init.cql");
            cassandraContainer.start();
        }
        return cassandraContainer;
    }

    private static final Pattern RE_URL = Pattern.compile("cassandra://(?:(.*):(.*)@)?(.*)(?::([0-9][1-9]*))?/(.*)/(.*)");
    private CqlSession createRemoteSession() {
        val value = System.getenv().get("CASSANDRA_URL");
        val matcher = RE_URL.matcher(value);
        if (!matcher.matches())
            throw new IllegalArgumentException("Invalid CASSANDRA_URL: " + value);
        val port = matcher.group(4) != null ? Integer.parseInt(matcher.group(4)) : 9042;
        val builder = CqlSession.builder()
                .addContactPoint(new InetSocketAddress(matcher.group(3), port))
                .withLocalDatacenter(matcher.group(5))
                .withKeyspace(matcher.group(6));
        if (matcher.group(1) != null)
            builder.withAuthCredentials(matcher.group(1), matcher.group(2));
        return builder.build();
    }

    @Override
    public void beforeAll(ExtensionContext context) {
        injectStaticFields(context.getRequiredTestClass());
    }

    @Override
    public void beforeEach(ExtensionContext context) {
        context.getRequiredTestInstances().getAllInstances() //
                .forEach(this::injectInstanceFields);
    }

    private void injectStaticFields(Class<?> testClass) {
        injectFields(null, testClass, ReflectionUtils::isStatic);
    }

    private void injectInstanceFields(Object instance) {
        injectFields(instance, instance.getClass(), ReflectionUtils::isNotStatic);
    }

    private void injectFields(Object testInstance, Class<?> testClass,
                              Predicate<Field> predicate) {

        findAnnotatedFields(testClass, CassandraSession.class, predicate).forEach(field -> {
            assertNonFinalField(field);
            assertSupportedType("field", field.getType());

            try {
                makeAccessible(field).set(testInstance, createSession());
            }
            catch (Throwable t) {
                ExceptionUtils.throwAsUncheckedException(t);
            }
        });
    }

    private void assertNonFinalField(Field field) {
        if (ReflectionUtils.isFinal(field)) {
            throw new ExtensionConfigurationException("@CassandraSession field [" + field + "] must not be declared as final.");
        }
    }

    private void assertSupportedType(String target, Class<?> type) {
        if (type != CqlSession.class) {
            throw new ExtensionConfigurationException("Can only resolve @CassandraSession " + target + " of type "
                    + CqlSession.class.getName() + " but was: " + type.getName());
        }
    }

    @Override
    public boolean supportsParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        boolean annotated = parameterContext.isAnnotated(CassandraSession.class);
        if (annotated && parameterContext.getDeclaringExecutable() instanceof Constructor) {
            throw new ParameterResolutionException(
                    "@CassandraSession is not supported on constructor parameters. Please use field injection instead.");
        }
        return annotated;
    }

    @Override
    public Object resolveParameter(ParameterContext parameterContext, ExtensionContext extensionContext) throws ParameterResolutionException {
        Class<?> parameterType = parameterContext.getParameter().getType();
        assertSupportedType("parameter", parameterType);
        return createSession();
    }

    @Override
    public void afterAll(ExtensionContext context) {
        if (cassandraContainer != null)
            cassandraContainer.stop();
    }
}
