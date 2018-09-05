module stroom.util {
    exports stroom.io;
    exports stroom.util;
    exports stroom.util.cache;
    exports stroom.util.cert;
    exports stroom.util.collections;
    exports stroom.util.concurrent;
    exports stroom.util.config;
    exports stroom.util.date;
    exports stroom.util.io;
    exports stroom.util.json;
    exports stroom.util.lifecycle;
    exports stroom.util.logging;
    exports stroom.util.scheduler;
    exports stroom.util.thread;
    exports stroom.util.web;
    exports stroom.util.xml;
    exports stroom.util.zip;

    requires stroom.util.shared;

    requires org.apache.commons.codec;
    requires com.google.common; //guava
    requires com.fasterxml.jackson.annotation;
    requires com.fasterxml.jackson.core;
    requires com.fasterxml.jackson.databind;
    requires javax.servlet.api;
    requires javax.inject;
    requires java.xml.bind;
    requires java.desktop;
    requires slf4j.api;
}