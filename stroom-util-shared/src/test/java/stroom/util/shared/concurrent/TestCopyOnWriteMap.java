/*
 * This class was copied from
 * https://bitbucket.org/atlassian/atlassian-util-concurrent/src/master/
 * Modified to use Junit Jupiter and AssertJ
 */

/*
 * Copyright 2008 Atlassian Pty Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.util.shared.concurrent;

import stroom.util.shared.concurrent.AbstractCopyOnWriteMap.View;
import stroom.util.shared.concurrent.MapBuilder.E;

import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.io.Serial;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;

import static java.util.Arrays.asList;

// Copied class, too much hassle to make it look nice, and it makes it harder to
// diff against upstream changes, so ignore CS.
@SuppressWarnings({"checkstyle:EmptyCatchBlock", "Indentation", "LineLength"})
public class TestCopyOnWriteMap {

    @Test
    public void factoryCalledOnConstructor() {
        final AtomicInteger count = new AtomicInteger();
        final Map<String, String> init = MapBuilder.build("1", "o1", "2", "o2", "3", "o3");
        final Map<String, String> map = new CopyOnWriteMap<String, String>(init, View.Type.STABLE) {
            private static final long serialVersionUID = 8866224559807093002L;

            @Override
            public <N extends Map<? extends String, ? extends String>> Map<String, String> copy(final N map) {
                count.getAndIncrement();
                return new HashMap<>(map);
            }
        };
        assertEquals(1, count.get());
        assertEquals(3, map.size());
        assertTrue(map.containsKey("2"));
        assertTrue(map.containsValue("o3"));
        assertEquals("o1", map.get("1"));
    }

    @Test
    public void factoryCalledOnWrite() {
        final AtomicInteger count = new AtomicInteger();
        final Map<String, String> map = new CopyOnWriteMap<String, String>(View.Type.STABLE) {
            private static final long serialVersionUID = -3858713272422952372L;

            @Override
            public <N extends Map<? extends String, ? extends String>> Map<String, String> copy(final N map) {
                count.getAndIncrement();
                return new HashMap<>(map);
            }
        };

        assertEquals("should be called in ctor", 1, count.get());
        map.put("test", "test");
        assertEquals("should be called in put", 2, count.get());
        assertEquals(1, map.size());
        assertTrue(map.containsKey("test"));
        assertTrue(map.containsValue("test"));
        assertEquals("should not be called in reads", 2, count.get());
        map.putAll(MapBuilder.build("1", "test1", "2", "test2", "3", "test3"));
        assertEquals("should be called in putAll", 3, count.get());
        assertEquals(4, map.size());
        assertTrue(map.containsKey("1"));
        assertTrue(map.containsValue("test3"));
        map.remove("2");
        assertEquals("should be called in remove", 4, count.get());
        assertEquals(3, map.size());
        assertFalse(map.containsValue("test2"));
        map.clear();
        assertEquals("should be called in clear", 5, count.get());
        assertEquals(0, map.size());
    }

    @Test
    public void delegateHashMap() throws Exception {
        final Map<String, String> map =
                MapBuilder.<String, String>builder().add("key", "value").toMap();
        final CopyOnWriteMap<String, String> cowMap =
                CopyOnWriteMap.<String, String>builder().addAll(map).newHashMap();
        assertEquals(map, cowMap);
        assertEquals(map.hashCode(), cowMap.hashCode());
        assertEquals(map.toString(), cowMap.toString());
    }

    @Test
    public void delegateKeySet() throws Exception {
        final Map<String, String> map =
                MapBuilder.<String, String>builder().add("key", "value").toMap();
        final CopyOnWriteMap<String, String> cowMap =
                CopyOnWriteMap.<String, String>builder().addAll(map).newHashMap();
        assertEquals(map.keySet(), cowMap.keySet());
        assertEquals(map.keySet().hashCode(), cowMap.keySet().hashCode());
        assertEquals(map.keySet().toString(), cowMap.keySet().toString());
    }

    @Test
    public void delegateEqualityValues() throws Exception {
        final Map<String, String> map =
                MapBuilder.<String, String>builder().add("key", "value").toMap();
        final CopyOnWriteMap<String, String> cowMap =
                CopyOnWriteMap.<String, String>builder().addAll(map).newHashMap();
        assertEquals(new ArrayList<>(map.values()), new ArrayList<>(cowMap.values()));
        assertEquals(new ArrayList<>(map.values()).hashCode(), new ArrayList<>(cowMap.values()).hashCode());
        assertEquals(map.values().toString(), cowMap.values().toString());
    }

    @Test
    public void delegateEntrySet() throws Exception {
        final Map<String, String> map =
                MapBuilder.<String, String>builder().add("key", "value").toMap();
        final CopyOnWriteMap<String, String> cowMap =
                CopyOnWriteMap.<String, String>builder().addAll(map).newHashMap();
        assertEquals(map.entrySet(), cowMap.entrySet());
        assertEquals(map.entrySet().hashCode(), cowMap.entrySet().hashCode());
        assertEquals(map.entrySet().toString(), cowMap.entrySet().toString());
    }

    @Test
    public void delegateLinked() throws Exception {
        final Map<String, String> map =
                MapBuilder.<String, String>builder().add("key", "value").toMap();
        final CopyOnWriteMap<String, String> cowMap =
                CopyOnWriteMap.<String, String>builder().addAll(map).newLinkedMap();
        assertEquals(map, cowMap);
        assertEquals(map.hashCode(), cowMap.hashCode());
        assertEquals(map.toString(), cowMap.toString());
    }

    @Test
    public void delegateKeySetLinked() throws Exception {
        final Map<String, String> map =
                MapBuilder.<String, String>builder().add("key", "value").toMap();
        final CopyOnWriteMap<String, String> cowMap =
                CopyOnWriteMap.<String, String>builder().addAll(map).newLinkedMap();
        assertEquals(map.keySet(), cowMap.keySet());
        assertEquals(map.keySet().hashCode(), cowMap.keySet().hashCode());
        assertEquals(map.keySet().toString(), cowMap.keySet().toString());
    }

    @Test
    public void delegateValuesLinked() throws Exception {
        final Map<String, String> map =
                MapBuilder.<String, String>builder().add("key", "value").toMap();
        final CopyOnWriteMap<String, String> cowMap =
                CopyOnWriteMap.<String, String>builder().addAll(map).newLinkedMap();
        assertEquals(new ArrayList<>(map.values()), new ArrayList<>(cowMap.values()));
        assertEquals(new ArrayList<>(map.values()).hashCode(), new ArrayList<>(cowMap.values()).hashCode());
        assertEquals(map.values().toString(), cowMap.values().toString());
    }

    @Test
    public void delegateEntrySetLinked() throws Exception {
        final Map<String, String> map =
                MapBuilder.<String, String>builder().add("key", "value").toMap();
        final CopyOnWriteMap<String, String> cowMap =
                CopyOnWriteMap.<String, String>builder().addAll(map).newLinkedMap();
        assertEquals(map.entrySet(), cowMap.entrySet());
        assertEquals(map.entrySet().hashCode(), cowMap.entrySet().hashCode());
        assertEquals(map.entrySet().toString(), cowMap.entrySet().toString());
    }

    @Test
    public void putIfAbsentWorksIfAbsent() throws Exception {
        final Map<String, String> init =
                MapBuilder.<String, String>builder().add("key", "value").toMap();
        final ConcurrentMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertNull(map.putIfAbsent("key2", "value2"));
        assertEquals(2, map.size());
        assertTrue(map.containsKey("key2"));
        assertTrue(map.containsValue("value2"));
    }

    @Test
    public void putIfAbsentFailsIfPresent() throws Exception {
        final Map<String, String> init = MapBuilder.<String, String>builder()
                .add("key", "value")
                .add("key2", "value2")
                .toMap();
        final ConcurrentMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        map.putIfAbsent("key2", "value3");
        assertNotNull(map.putIfAbsent("key2", "value2"));
        assertEquals(2, map.size());
        assertTrue(map.containsKey("key2"));
        assertTrue(map.containsValue("value2"));
        assertFalse(map.containsValue("value3"));
    }

    @Test
    public void removeWorksIfValueSame() throws Exception {
        final Map<String, String> init = MapBuilder.<String, String>builder()
                .add("key", "value")
                .add("key2", "value2")
                .toMap();
        final ConcurrentMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertTrue(map.remove("key2", "value2"));
        assertEquals(1, map.size());
        assertFalse(map.containsKey("key2"));
        assertFalse(map.containsValue("value2"));
    }

    @Test
    public void removeFailsIfValueDifferent() throws Exception {
        final Map<String, String> init = MapBuilder.<String, String>builder()
                .add("key", "value")
                .add("key2", "value2")
                .toMap();
        final ConcurrentMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertFalse(map.remove("key2", "value3"));
        assertEquals(2, map.size());
        assertTrue(map.containsKey("key2"));
        assertTrue(map.containsValue("value2"));
        assertFalse(map.containsValue("value3"));
    }

    @Test
    public void unconditionalReplaceWorksIfKeyMapped() throws Exception {
        final Map<String, String> init = MapBuilder.<String, String>builder()
                .add("key", "value")
                .add("key2", "value2")
                .toMap();
        final ConcurrentMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertNotNull(map.replace("key2", "value3"));
        assertEquals(2, map.size());
        assertTrue(map.containsKey("key2"));
        assertTrue(map.containsValue("value3"));
        assertFalse(map.containsValue("value2"));
    }

    @Test
    public void unconditionalReplaceFailsIfKeyMissing() throws Exception {
        final Map<String, String> init = MapBuilder.<String, String>builder()
                .add("key", "value")
                .add("key2", "value2")
                .toMap();
        final ConcurrentMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertNull(map.replace("key3", "value3"));
        assertEquals(2, map.size());
        assertTrue(map.containsKey("key2"));
        assertTrue(map.containsValue("value2"));
        assertFalse(map.containsKey("key3"));
        assertFalse(map.containsValue("value3"));
    }

    @Test
    public void conditionalReplaceWorksIfKeyAndValueMapped() throws Exception {
        final Map<String, String> init = MapBuilder.<String, String>builder()
                .add("key", "value")
                .add("key2", "value2")
                .toMap();
        final ConcurrentMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertTrue(map.replace("key2", "value2", "value3"));
        assertEquals(2, map.size());
        assertTrue(map.containsKey("key2"));
        assertTrue(map.containsValue("value3"));
        assertFalse(map.containsValue("value2"));
    }

    @Test
    public void conditionalReplaceWorksIfValueNull() throws Exception {
        final Map<String, String> init = MapBuilder.<String, String>builder()
                .add("key", "value")
                .add("key2", null)
                .toMap();
        final ConcurrentMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertTrue(map.replace("key2", null, "value3"));
        assertEquals(2, map.size());
        assertTrue(map.containsKey("key2"));
        assertTrue(map.containsValue("value3"));
        assertFalse(map.containsValue(null));
    }

    @Test
    public void conditionalReplaceFailsIfValueDifferent() throws Exception {
        final Map<String, String> init = MapBuilder.<String, String>builder()
                .add("key", "value")
                .add("key2", "value2")
                .toMap();
        final ConcurrentMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertFalse(map.replace("key2", "value3", "value4"));
        assertEquals(2, map.size());
        assertTrue(map.containsKey("key2"));
        assertTrue(map.containsValue("value2"));
        assertFalse(map.containsValue("value3"));
        assertFalse(map.containsValue("value4"));
    }

    @Test
    public void conditionalReplaceFailsIfKeyMissing() throws Exception {
        final Map<String, String> init = MapBuilder.<String, String>builder()
                .add("key", "value")
                .add("key2", "value2")
                .toMap();
        final ConcurrentMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertFalse(map.replace("key3", "value2", "value3"));
        assertEquals(2, map.size());
        assertTrue(map.containsKey("key2"));
        assertTrue(map.containsValue("value2"));
        assertFalse(map.containsKey("key3"));
        assertFalse(map.containsValue("value3"));
    }

    @Test
    public void modifiableValues() throws Exception {
        final AtomicInteger count = new AtomicInteger();
        final Map<String, String> init = new MapBuilder<String, String>()
                .add("test", "test")
                .add("testing", "testing")
                .add("sup", "tester")
                .toMap();
        final Map<String, String> map = new CopyOnWriteMap<String, String>(init, View.Type.LIVE) {
            private static final long serialVersionUID = 3275978982528321604L;

            @Override
            public <N extends Map<? extends String, ? extends String>> Map<String, String> copy(final N map) {
                count.getAndIncrement();
                return new HashMap<>(map);
            }
        };
        assertEquals(1, count.get());
        final Collection<String> values = map.values();
        try {
            values.add("something");
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        try {
            values.addAll(asList("one", "two", "three"));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        final Iterator<String> iterator = values.iterator();
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        try {
            iterator.remove();
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        assertFalse(values.remove("blah"));
        assertEquals("not modified if element not present to be removed", 1, count.get());
        assertTrue(values.remove("test"));
        assertEquals(2, count.get());
        assertEquals(2, map.size());
        assertFalse(values.retainAll(asList("testing", "tester")));
        assertEquals(3, count.get());
        assertEquals(2, map.size());
        assertTrue(values.removeAll(asList("test", "testing")));
        assertEquals(4, count.get());
        assertEquals(1, map.size());
        values.clear();
        assertTrue(map.isEmpty());
    }

    @Test
    public void modifiableEntrySet() throws Exception {
        final AtomicInteger count = new AtomicInteger();
        final Map<String, String> init = new MapBuilder<String, String>()
                .add("test", "test")
                .add("testing", "testing")
                .add("tester", "tester")
                .toMap();
        final Map<String, String> map = new CopyOnWriteMap<String, String>(init, View.Type.LIVE) {
            private static final long serialVersionUID = -2882860445706454721L;

            @Override
            public <N extends Map<? extends String, ? extends String>> Map<String, String> copy(final N map) {
                count.getAndIncrement();
                return new HashMap<>(map);
            }
        };
        assertEquals(1, count.get());
        final Collection<Entry<String, String>> entries = map.entrySet();

        try {
            entries.add(new E("something"));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        try {
            entries.addAll(asList(new E("one"), new E("two"), new E("three")));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        final Iterator<Entry<String, String>> iterator = entries.iterator();
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        try {
            iterator.remove();
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        assertFalse(entries.remove("blah"));
        assertEquals("not modified if element not present to be removed", 1, count.get());
        assertTrue(entries.remove(new E("test")));
        assertEquals(2, count.get());
        assertEquals(2, map.size());
        assertFalse(entries.retainAll(asList(new E("testing"), new E("tester"))));
        assertEquals(3, count.get());
        assertEquals(2, map.size());
        assertTrue(entries.removeAll(asList(new E("test"), new E("testing"))));
        assertEquals(4, count.get());
        assertEquals(1, map.size());
        entries.clear();
        assertTrue(map.isEmpty());
        map.put("key", "key");
        assertTrue(entries.contains(new E("key")));
    }

    @Test
    public void modifiableKeySet() throws Exception {
        final AtomicInteger count = new AtomicInteger();

        final Map<String, String> init = new MapBuilder<String, String>()
                .add("test", "test")
                .add("testing", "testing")
                .add("tester", "tester")
                .toMap();
        final Map<String, String> map = new CopyOnWriteMap<String, String>(init, View.Type.LIVE) {
            private static final long serialVersionUID = 7273654247572679525L;

            @Override
            public <N extends Map<? extends String, ? extends String>> Map<String, String> copy(final N map) {
                count.getAndIncrement();
                return new HashMap<>(map);
            }
        };
        assertEquals(1, count.get());
        final Collection<String> keys = map.keySet();
        try {
            keys.add("something");
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        try {
            keys.addAll(asList("one", "two", "three"));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        final Iterator<String> iterator = keys.iterator();
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        try {
            iterator.remove();
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        assertFalse(keys.remove("blah"));
        assertEquals("not modified if element not present to be removed", 1, count.get());
        assertTrue(keys.remove("test"));
        assertEquals(2, count.get());
        assertEquals(2, map.size());
        assertFalse(keys.retainAll(asList("testing", "tester")));
        assertEquals(3, count.get());
        assertEquals(2, map.size());
        assertTrue(keys.removeAll(asList("test", "testing")));
        assertEquals(4, count.get());
        assertEquals(1, map.size());
        keys.clear();
        assertTrue(map.isEmpty());
        map.put("key", "key");
        assertTrue(keys.contains("key"));
    }

    @Test
    public void unmodifiableValues() throws Exception {
        final AtomicInteger count = new AtomicInteger();
        final Map<String, String> init = new MapBuilder<String, String>()
                .add("test", "test")
                .add("testing", "testing")
                .add("sup", "tester")
                .toMap();
        final Map<String, String> map = new CopyOnWriteMap<String, String>(init, View.Type.STABLE) {
            private static final long serialVersionUID = 3275978982528321604L;

            @Override
            public <N extends Map<? extends String, ? extends String>> Map<String, String> copy(final N map) {
                count.getAndIncrement();
                return new HashMap<>(map);
            }
        };
        assertEquals(1, count.get());
        final Collection<String> values = map.values();
        try {
            values.add("something");
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        try {
            values.addAll(asList("one", "two", "three"));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        final Iterator<String> iterator = values.iterator();
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        try {
            iterator.remove();
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        try {
            values.remove("blah");
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        try {
            values.retainAll(asList("testing", "tester"));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        try {
            values.removeAll(asList("test", "testing"));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        try {
            values.clear();
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        map.put("lalala", "lalala");
        assertFalse(values.contains("lalala"));
        assertUnmodifiableCollection(values, "lalala");
    }

    @Test
    public void unmodifiableEntrySet() throws Exception {
        final AtomicInteger count = new AtomicInteger();
        final Map<String, String> init = new MapBuilder<String, String>()
                .add("test", "test")
                .add("testing", "testing")
                .add("tester", "tester")
                .toMap();
        final Map<String, String> map = new CopyOnWriteMap<String, String>(init, View.Type.STABLE) {
            private static final long serialVersionUID = -2882860445706454721L;

            @Override
            public <N extends Map<? extends String, ? extends String>> Map<String, String> copy(final N map) {
                count.getAndIncrement();
                return new HashMap<>(map);
            }
        };
        assertEquals(1, count.get());
        final Collection<Entry<String, String>> entries = map.entrySet();

        try {
            entries.add(new E("something"));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        try {
            entries.addAll(asList(new E("one"), new E("two"), new E("three")));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        final Iterator<Entry<String, String>> iterator = entries.iterator();
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        try {
            iterator.remove();
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        try {
            assertFalse(entries.remove("blah"));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        try {
            entries.retainAll(asList(new E("testing"), new E("tester")));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        try {
            entries.removeAll(asList(new E("test"), new E("testing")));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        try {
            entries.clear();
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        map.put("key", "key");
        assertFalse(entries.contains(new E("key")));
        assertUnmodifiableCollection(entries, new E("lalala"));
    }

    @Test
    public void unmodifiableKeySet() throws Exception {
        final AtomicInteger count = new AtomicInteger();

        final Map<String, String> init = new MapBuilder<String, String>()
                .add("test", "test")
                .add("testing", "testing")
                .add("tester", "tester")
                .toMap();
        final Map<String, String> map = new CopyOnWriteMap<String, String>(init, View.Type.STABLE) {
            private static final long serialVersionUID = 7273654247572679525L;

            @Override
            public <N extends Map<? extends String, ? extends String>> Map<String, String> copy(final N map) {
                count.getAndIncrement();
                return new HashMap<>(map);
            }
        };
        assertEquals(1, count.get());
        final Collection<String> keys = map.keySet();
        try {
            keys.add("something");
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        try {
            keys.addAll(asList("one", "two", "three"));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        final Iterator<String> iterator = keys.iterator();
        assertTrue(iterator.hasNext());
        assertNotNull(iterator.next());
        try {
            iterator.remove();
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        assertEquals(1, count.get());
        try {
            keys.remove("blah");
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        try {
            keys.remove("test");
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        try {
            keys.retainAll(asList("testing", "tester"));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        try {
            keys.removeAll(asList("test", "testing"));
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        try {
            keys.clear();
            fail("UnsupportedOp expected");
        } catch (final UnsupportedOperationException ignore) {
        }
        map.put("key", "lala");
        assertFalse(keys.contains("key"));
        assertUnmodifiableCollection(keys, "key");
    }

    @Test
    public void nullMap() throws Exception {
        Assertions.assertThatThrownBy(() -> {
                    new CopyOnWriteMap<String, String>(null, View.Type.STABLE) {
                        @Serial
                        private static final long serialVersionUID = 4223850632932526917L;

                        // /CLOVER:OFF
                        @Override
                        public <N extends Map<? extends String, ? extends String>> Map<String, String> copy(final N map) {
                            return new HashMap<>(map);
                        }

                        ;
                        // /CLOVER:ON
                    };
                })
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void nullViewType() throws Exception {
        Assertions.assertThatThrownBy(() -> {
                    new CopyOnWriteMap<String, String>(new HashMap<>(), null) {
                        @Serial
                        private static final long serialVersionUID = 4223850632932526917L;

                        // /CLOVER:OFF
                        @Override
                        public <N extends Map<? extends String, ? extends String>> Map<String, String> copy(final N map) {
                            return new HashMap<>(map);
                        }

                        ;
                        // /CLOVER:ON
                    };
                })
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void copyFunctionReturnsNull() throws Exception {
        Assertions.assertThatThrownBy(() -> {
                    new CopyOnWriteMap<String, String>(View.Type.STABLE) {
                        @Serial
                        private static final long serialVersionUID = 831716474176011289L;

                        @Override
                        public <N extends Map<? extends String, ? extends String>> Map<String, String> copy(final N map) {
                            return null;
                        }

                        ;
                    };
                })
                .isInstanceOf(NullPointerException.class);
    }

    @Test
    public void serializableHashMap() {
        assertMutableMapSerializable(CopyOnWriteMap.builder().newHashMap());
    }

    @Test
    public void serializableLinkedMap() {
        assertMutableMapSerializable(CopyOnWriteMap.builder().newLinkedMap());
    }

    @Test
    public void toStringTest() throws Exception {
        final AtomicReference<Map<String, String>> ref = new AtomicReference<Map<String, String>>();
        final CopyOnWriteMap<String, String> cowMap = new CopyOnWriteMap<String, String>(View.Type.STABLE) {
            private static final long serialVersionUID = -17380087385174856L;

            @Override
            protected <N extends Map<? extends String, ? extends String>> java.util.Map<String, String> copy(
                    final N map) {
                ref.set(new HashMap<>(map));
                return ref.get();
            }

            ;
        };
        assertEquals(ref.get().toString(), cowMap.toString());
    }

    @Test
    public void isEmpty() throws Exception {
        final CopyOnWriteMap<String, String> cowMap =
                CopyOnWriteMap.<String, String>builder().newHashMap();
        assertTrue(cowMap.isEmpty());
        assertTrue(cowMap.keySet().isEmpty());
        assertTrue(cowMap.entrySet().isEmpty());
        assertTrue(cowMap.values().isEmpty());
        cowMap.put("1", "1");
        assertFalse(cowMap.isEmpty());
        assertFalse(cowMap.keySet().isEmpty());
        assertFalse(cowMap.entrySet().isEmpty());
        assertFalse(cowMap.values().isEmpty());
    }

    @Test
    public void equality() throws Exception {
        final Map<String, String> init = new MapBuilder<String, String>()
                .add("test", "test")
                .add("testing", "testing")
                .toMap();
        final CopyOnWriteMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertEquals(init, map);
        assertEquals(map, init);
        assertEquals(init.hashCode(), map.hashCode());
        assertEquals(map.hashCode(), init.hashCode());
        assertEquals(init.keySet(), map.keySet());
        assertEquals(map.keySet(), init.keySet());
        assertEquals(init.entrySet(), map.entrySet());
        assertEquals(map.entrySet(), init.entrySet());
        assertFalse(init.values().equals(map.values()));
        assertFalse(map.values().equals(init.values()));
    }

    @Test
    public void toArray() throws Exception {
        final Map<String, String> init = new MapBuilder<String, String>()
                .add("test", "test")
                .add("testing", "testing")
                .toMap();
        final CopyOnWriteMap<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertArrayEquals(init.keySet().toArray(new String[2]), map.keySet().toArray(new String[2]));
        assertArrayEquals(init.values().toArray(new String[2]), map.values().toArray(new String[2]));
        assertArrayEquals(
                init.entrySet().toArray(new Map.Entry[2]), map.entrySet().toArray(new Map.Entry[2]));
    }

    @Test
    public void contains() throws Exception {
        final Map<String, String> init = MapBuilder.build("1", "o1", "2", "o2", "3", "o3");
        final Map<String, String> map =
                CopyOnWriteMap.<String, String>builder().addAll(init).newHashMap();
        assertTrue(map.containsKey("2"));
        assertTrue(map.containsValue("o2"));
        assertTrue(map.keySet().contains("2"));
        assertTrue(map.keySet().containsAll(asList(new String[]{"1", "2", "3"})));
        assertTrue(map.values().contains("o2"));
        assertTrue(map.values().containsAll(asList(new String[]{"o1", "o2", "o3"})));
    }

    static void assertMutableMapSerializable(Map<Object, Object> map) {
        map.put("1", "one");
        map = assertSerializable(map);
        assertTrue(map.containsKey("1"));
        assertTrue(map.containsValue("one"));
        assertEquals("1", map.keySet().iterator().next());
        assertEquals("one", map.values().iterator().next());
        final Map.Entry<Object, Object> entry = map.entrySet().iterator().next();
        assertEquals("1", entry.getKey());
        assertEquals("one", entry.getValue());
    }

    static Map<Object, Object> assertSerializable(final Map<Object, Object> map) {
        final Map<Object, Object> result = serialize(map);
        assertEquals(map, result);
        return result;
    }

    private static void assertEquals(final String msg, final Object o1, final Object o2) {
        Assertions.assertThat(o2)
                .withFailMessage(msg)
                .isEqualTo(o1);
    }

    private static void assertEquals(final Object o1, final Object o2) {
        Assertions.assertThat(o2)
                .isEqualTo(o1);
    }

    private static void assertTrue(final boolean bool) {
        Assertions.assertThat(bool)
                .isTrue();
    }

    private static void assertFalse(final boolean bool) {
        Assertions.assertThat(bool)
                .isFalse();
    }

    private static void assertNull(final Object object) {
        Assertions.assertThat(object)
                .isNull();
    }

    private static void assertNotNull(final Object object) {
        Assertions.assertThat(object)
                .isNotNull();
    }

    private static void fail(final String msg) {
        Assertions.fail(msg);
    }

    private static <T> void assertArrayEquals(final T[] expected, final T[] actual) {
        Assertions.assertThat(actual)
                .isEqualTo(expected);
    }

    private static void assertThrowsUnsupportedOp(final Runnable runnable) {
        Assertions.assertThatThrownBy(runnable::run);
    }

    static <T> void assertUnmodifiableCollection(final Collection<T> coll, final T element) {
        assertThrowsUnsupportedOp(coll::clear);

        assertThrowsUnsupportedOp(() -> coll.remove(element));

        assertThrowsUnsupportedOp(() -> coll.removeAll(Collections.EMPTY_LIST));

        assertThrowsUnsupportedOp(() -> coll.add(element));

        final Collection<T> empty = Collections.emptyList();
        assertThrowsUnsupportedOp(() -> coll.addAll(empty));

        assertThrowsUnsupportedOp(() -> coll.retainAll(empty));

        assertThrowsUnsupportedOp(() -> {
            final Iterator<?> it = coll.iterator();
            it.next();
            it.remove();
        });
    }

    @SuppressWarnings("unchecked")
    static <T> T serialize(final T map) {
        final ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        try {
            new ObjectOutputStream(bytes).writeObject(map);
            return (T) new ObjectInputStream(new ByteArrayInputStream(bytes.toByteArray())).readObject();
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }
}

class MapBuilder<K, V> {

    private final Map<K, V> map = new HashMap<K, V>();

    static <S> Map<S, S> build(@SuppressWarnings("unchecked") final S... elements) {
        if (elements.length % 2 != 0) {
            throw new IllegalArgumentException("must have even number of elements: " + elements.length);
        }
        final MapBuilder<S, S> result = new MapBuilder<S, S>();
        for (int i = 0; i < elements.length; i = i + 2) {
            result.add(elements[i], elements[i + 1]);
        }
        return result.toMap();
    }

    static <K, V> MapBuilder<K, V> builder() {
        return new MapBuilder<>();
    }

    MapBuilder<K, V> add(final K key, final V value) {
        map.put(key, value);
        return this;
    }

    Entry<K, V> entry(final K key, final V value) {
        return new Entry<K, V>() {
            public K getKey() {
                return key;
            }

            public V getValue() {
                return value;
            }

            public V setValue(final V arg0) {
                throw new UnsupportedOperationException();
            }

            ;
        };
    }

    Map<K, V> toMap() {
        return Collections.unmodifiableMap(new HashMap<>(map));
    }

    static class E implements Map.Entry<String, String> {

        final String e;

        public E(final String e) {
            this.e = e;
        }

        public String getKey() {
            return e;
        }

        public String getValue() {
            return e;
        }

        public String setValue(final String value) {
            throw new RuntimeException("should not be called, don't use UnsupportedOp here");
        }
    }
}
