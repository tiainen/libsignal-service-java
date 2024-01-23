package org.whispersystems.signalservice.api.push;

import java.util.UUID;
import junit.framework.TestCase;
import org.whispersystems.signalservice.api.push.ServiceId.PNI;

/**
 *
 * @author johan
 */
public class ServiceIdTest extends TestCase {

    public void testServiceId() {
        UUID uuid = UUID.randomUUID();
        ServiceId serviceId = ServiceId.from(uuid);
        assertNotNull(serviceId);
    }
    
    public void testPni() {
        UUID uuid = UUID.randomUUID();
        String uuidString = uuid.toString();
        ServiceId serviceId = PNI.from(UUID.fromString(uuidString));
        assertNotNull(serviceId);   
        ServiceId serviceId2 = PNI.parseOrNull("PNI:"+uuidString);
        assertNotNull(serviceId2);
        assertEquals(serviceId, serviceId2);
    }
    
}
