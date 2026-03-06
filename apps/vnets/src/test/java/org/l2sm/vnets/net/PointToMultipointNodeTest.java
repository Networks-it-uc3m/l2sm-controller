package org.l2sm.vnets.net;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;

import org.junit.Test;
import org.onosproject.net.ConnectPoint;

public class PointToMultipointNodeTest {

    @Test
    public void rootPortIsReturned() {
        ConnectPoint root = cp("of:0000000000000001/1");
        PointToMultipointNode node = new PointToMultipointNode(root);

        assertEquals(root, node.getRootPort());
    }

    @Test
    public void addChildWithNextCreatesNode() {
        PointToMultipointNode node = new PointToMultipointNode(cp("of:0000000000000001/1"));

        PointToMultipointNode child = node.addChild(cp("of:0000000000000001/2"), cp("of:0000000000000002/1"));

        assertNotNull(child);
        assertEquals(cp("of:0000000000000002/1"), child.getRootPort());
        assertEquals(child, node.getChild(cp("of:0000000000000001/2")));
    }

    @Test
    public void addChildWithNullNextStoresNullChild() {
        PointToMultipointNode node = new PointToMultipointNode(cp("of:0000000000000001/1"));

        PointToMultipointNode child = node.addChild(cp("of:0000000000000001/2"), null);

        assertNull(child);
        assertTrue(node.getChildren().containsKey(cp("of:0000000000000001/2")));
        assertNull(node.getChild(cp("of:0000000000000001/2")));
    }

    @Test
    public void getChildrenNodesReflectsAddedChildren() {
        PointToMultipointNode node = new PointToMultipointNode(cp("of:0000000000000001/1"));

        node.addChild(cp("of:0000000000000001/2"), cp("of:0000000000000002/1"));
        node.addChild(cp("of:0000000000000001/3"), cp("of:0000000000000003/1"));

        assertEquals(2, node.getChildrenNodes().size());
    }

    @Test
    public void getChildReturnsNullForUnknownPort() {
        PointToMultipointNode node = new PointToMultipointNode(cp("of:0000000000000001/1"));

        assertNull(node.getChild(cp("of:0000000000000099/9")));
    }

    @Test
    public void toStringContainsRootAndChildPorts() {
        PointToMultipointNode node = new PointToMultipointNode(cp("of:0000000000000001/1"));
        node.addChild(cp("of:0000000000000001/2"), cp("of:0000000000000002/1"));

        String rendered = node.toString();

        assertTrue(rendered.contains("of:0000000000000001/1"));
        assertTrue(rendered.contains("of:0000000000000001/2"));
    }

    private static ConnectPoint cp(String value) {
        return ConnectPoint.deviceConnectPoint(value);
    }
}
