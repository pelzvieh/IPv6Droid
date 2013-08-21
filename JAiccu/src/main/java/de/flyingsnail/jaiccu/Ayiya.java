package de.flyingsnail.jaiccu;

import android.util.Log;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.Socket;
import java.net.UnknownHostException;

/**
 * Created by pelzi on 17.08.13.
 *
 * SixXS - Automatic IPv6 Connectivity Configuration Utility
 *
 * AYIYA - Anything In Anything
 *
 * Copyright 2003-2005 SixXS - http://www.sixxs.net
 * Copyright 2013 Dr. Andreas Feldner
 * See license.
 */
public class Ayiya {
    /**
     *  Anything In Anything - AYIYA (uses UDP in our case)
     */
    public static final int PORT = 5072;

    /**
     * AYIYA version (which document this should conform to)
     * Per draft-massar-v6ops-ayiya-02 (July 2004)
     */
    public static final String VERSION = "draft-02";

    /** Tag for Logger */
    private static final String TAG = Ayiya.class.getName();

    public void connect() {
        // @todo implement
        throw new IllegalStateException("Didn't implement this yet");
    }

    public Socket getSocket() {
        // @todo implement
        throw new IllegalStateException("Didn't implement this yet");
    }

    public void close() {
        // @todo implement
        throw new IllegalStateException("Didn't implement this yet");
    }

    // @TODO Prüfen, worfür diese genutzt werden. Enum oder Hashmap??
    enum ayiya_identities
    {
        ayiya_id_none,  /* None */
        ayiya_id_integer,	/* Integer */
        ayiya_id_string	/* ASCII String */
    };

    enum ayiya_hash
    {
        ayiya_hash_none,	/* No hash */
        ayiya_hash_md5,	/* MD5 Signature */
        ayiya_hash_sha1,	/* SHA1 Signature */
        ayiya_hash_umac	/* UMAC Signature (UMAC: Message Authentication Code using Universal Hashing / draft-krovetz-umac-04.txt */
    };

    enum ayiya_auth
    {
        ayiya_auth_none,	/* No authentication */
        ayiya_auth_sharedsecret,	/* Shared Secret */
        ayiya_auth_pgp	/* Public/Private Key */
    };

    enum ayiya_opcode
    {
        ayiya_op_noop,	/* No Operation */
        ayiya_op_forward,	/* Forward */
        ayiya_op_echo_request,	/* Echo Request */
        ayiya_op_echo_request_forward,	/* Echo Request and Forward */
        ayiya_op_echo_response,	/* Echo Response */
        ayiya_op_motd,	/* MOTD */
        ayiya_op_query_request,	/* Query Request */
        ayiya_op_query_response	/* Query Response */
    };


    /**
     * Constructor.
     * @param tunnel the specification of the tunnel to be dealt by this.
     */
    public Ayiya (TicTunnel tunnel) {
	    /* Resolve hTunnel entries */
        InetAddress pop;
        pop = tunnel.getIPv4Pop();

        InetAddress ipv6Local = tunnel.getIpv6Endpoint();

    }



}
