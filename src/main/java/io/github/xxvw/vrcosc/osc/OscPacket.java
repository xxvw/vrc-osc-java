package io.github.xxvw.vrcosc.osc;

/** A decoded or encodable OSC packet. */
public sealed interface OscPacket permits OscMessage, OscBundle {}
