package ch.sventschui.nexus.casc;

import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class InterpolatorTest {

    @Test
    void interpolateWithFile() {
        assertEquals("hello world", new Interpolator().interpolate("hello ${file:"+getClass().getClassLoader().getResource("test").getPath()+"}"));
    }

    @Test
    void interpolateWithEnvVar() {
        Map.Entry<String, String> envVar = System.getenv().entrySet().iterator().next();
        String key = envVar.getKey();
        String value = envVar.getValue();

        assertEquals("hello " + value, new Interpolator().interpolate("hello $"+key));
        assertEquals("hello " + value, new Interpolator().interpolate("hello ${"+key+"}"));
        assertEquals("hello " + value, new Interpolator().interpolate("hello ${"+key+":\"\"}"));
        assertEquals("hello " + value, new Interpolator().interpolate("hello ${"+key+":}"));
        assertEquals("hello " + value, new Interpolator().interpolate("hello ${"+key+":foo}"));
    }

    @Test
    void interpolateWithNonExistingEnvVar() {
        assertEquals("hello $IDONOTEXIST", new Interpolator().interpolate("hello $IDONOTEXIST"));
        assertEquals("hello ${IDONOTEXIST}", new Interpolator().interpolate("hello ${IDONOTEXIST}"));
        assertEquals("hello ", new Interpolator().interpolate("hello ${IDONOTEXIST:}"));
        assertEquals("hello ", new Interpolator().interpolate("hello ${IDONOTEXIST:\"\"}"));
        assertEquals("hello world", new Interpolator().interpolate("hello ${IDONOTEXIST:world}"));
        assertEquals("hello world", new Interpolator().interpolate("hello ${IDONOTEXIST:\"world\"}"));
    }
}