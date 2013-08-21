package de.flyingsnail.jaiccu;

import android.util.Log;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
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

/* @TODO how to deal with this? A class that supports conversion from / to this byte array would seem appropriate.
    struct ayiyahdr
    {
        #if BYTE_ORDER == BIG_ENDIAN
        uint32_t	ayh_idlen:  4;		// Identity Length
        uint32_t	ayh_idtype: 4;		// Identity Type 
        uint32_t	ayh_siglen: 4;		// Signature Length 
        uint32_t	ayh_hshmeth:4;		// Hashing Method 
        uint32_t	ayh_autmeth:4;		// Authentication Method 
        uint32_t	ayh_opcode: 4;		// Operation Code 
        uint32_t	ayh_nextheader:8;	// Next Header (PROTO_*) 
        #elif BYTE_ORDER == LITTLE_ENDIAN
        uint32_t	ayh_idtype: 4;		// Identity Type 
        uint32_t	ayh_idlen:  4;		// Identity Length 
        uint32_t	ayh_hshmeth:4;		// Hashing Method 
        uint32_t	ayh_siglen: 4;		// Signature Length 
        uint32_t	ayh_opcode: 4;		// Operation Code 
        uint32_t	ayh_autmeth:4;		// Authentication Method 
        uint32_t	ayh_nextheader:8;	// Next Header (PROTO_*) 
        #else
        #error unsupported endianness!
        #endif
        uint32_t	ayh_epochtime;		// Time in seconds since "00:00:00 1970-01-01 UTC" 
    };
    */

    /**
     * @todo find out what this is supposed to be
     * @param tunnel
     * @return
     */
    boolean ayiya (TicTunnel tunnel) {
	    /* Resolve hTunnel entries */
        InetAddress pop;
        try {
            pop = Inet4Address.getByName(tunnel.getIPv4Pop());
        } catch (UnknownHostException e) {
            Log.e(TAG, "Couldn't resolve PoP IPv4 " + tunnel.getIPv4Pop());
            throw new InvalidConfigurationException ("IPv4PoP", tunnel.getIPv4Pop(), e);
        }

        try {
            InetAddress ipv6Local = Inet6Address.getByName(tunnel.getIPv6Local());
        } catch (UnknownHostException e) {
            Log.e(TAG, "Couldn't resolve PoP IPv4 " + tunnel.getIPv4Pop());
            throw new InvalidConfigurationException ("IPv4PoP", tunnel.getIPv4Pop(), e);
        }

        return true;
    }

    /**
     * @todo find out what this is supposed to do
     */
    void beat() {

    }

    /* Tun -> Socket */
    void ayiya_reader(char *buf, unsigned int length);
    void ayiya_reader(char *buf, unsigned int length)
    {
        struct pseudo_ayh	*s = (struct pseudo_ayh *)buf, s2;
        int			lenout;
        SHA_CTX			sha1;
        sha1_byte		hash[SHA1_DIGEST_LENGTH];
        struct sockaddr_in	target;

	/* We tunnel over IPv4 */
        memcpy(&target.sin_addr, &ayiya_ipv4_pop, sizeof(target.sin_addr));
        target.sin_family = AF_INET;
        target.sin_port = htons(atoi(AYIYA_PORT));

	/* Prefill some standard AYIYA values */
        memset(&s, 0, sizeof(s));
        s2.ayh.ayh_idlen	= 4;			/* 2^4 = 16 bytes = 128 bits (IPv6 address) */
        s2.ayh.ayh_idtype	= ayiya_id_integer;
        s2.ayh.ayh_siglen	= 5;			/* 5*4 = 20 bytes = 160 bits (SHA1) */
        s2.ayh.ayh_hshmeth	= ayiya_hash_sha1;
        s2.ayh.ayh_autmeth	= ayiya_auth_sharedsecret;
        s2.ayh.ayh_opcode	= ayiya_op_forward;
        s2.ayh.ayh_nextheader	= IPPROTO_IPV6;

	/* Our IPv6 side of this tunnel */
        memcpy(&s2.identity, &ayiya_ipv6_local, sizeof(s2.identity));

	/* The payload */
        memcpy(&s2.payload, buf, length);

	/* Fill in the current time */
        s2.ayh.ayh_epochtime = htonl((u_long)time(NULL));

	/*
	 * The hash of the shared secret needs to be in the
	 * spot where we later put the complete hash
	 */
        memcpy(&s2.hash, ayiya_hash, sizeof(s2.hash));

	/* Generate a SHA1 */
        SHA1_Init(&sha1);
	/* Hash the complete AYIYA packet */
        SHA1_Update(&sha1, (sha1_byte *)&s2, sizeof(s2)-sizeof(s2.payload)+length);
	/* Store the hash in the packets hash */
        SHA1_Final(hash, &sha1);

	/* Store the hash in the actual packet */
        memcpy(&s2.hash, &hash, sizeof(s2.hash));

	/* Send it onto the network */
        length = sizeof(s2)-sizeof(s2.payload)+length;
        #if defined(_FREEBSD) || defined(_DFBSD) || defined(_OPENBSD) || defined(_DARWIN) || defined(_NETBSD)
        lenout = send(ayiya_socket->socket, (const char *)&s2, length, 0);
        #else
        lenout = sendto(ayiya_socket->socket, (const char *)&s2, length, 0, (struct sockaddr *)&target, sizeof(target));
        #endif
        if (lenout < 0)
        {
            ayiya_log(LOG_ERR, reader_name, NULL, 0, "Error (%d) while sending %u bytes to network: %s (%d)\n", lenout, length, strerror(errno), errno);
        }
        else if (length != (unsigned int)lenout)
        {
            ayiya_log(LOG_ERR, reader_name, NULL, 0, "Only %u of %u bytes sent to network: %s (%s)\n", lenout, length, strerror(errno), errno);
        }
    }

    struct tun_reader ayiya_tun = { (TUN_PROCESS)ayiya_reader };

/* Socket -> Tun */
    #ifndef _WIN32
    void *ayiya_writer(void UNUSED *arg);
    void *ayiya_writer(void UNUSED *arg)
    #else
    DWORD WINAPI ayiya_writer(LPVOID arg);
    DWORD WINAPI ayiya_writer(LPVOID arg)
    #endif
    {
        unsigned char		buf[2048];
        struct pseudo_ayh	*s = (struct pseudo_ayh *)buf;
        struct sockaddr_storage	ci;
        socklen_t		cl;
        int			i, n;
        unsigned int		payloadlen = 0;
        SHA_CTX			sha1;
        sha1_byte		their_hash[SHA1_DIGEST_LENGTH],
        our_hash[SHA1_DIGEST_LENGTH];

        ayiya_log(LOG_INFO, writer_name, NULL, 0, "(Socket to TUN) started\n");

	/* Tun/TAP device is now running */
        g_aiccu->tunrunning = true;

        while (true)
        {
            cl = sizeof(ci);
            memset(buf, 0, sizeof(buf));
            n = recvfrom(ayiya_socket->socket, (char *)buf, sizeof(buf), 0, (struct sockaddr *)&ci, &cl);

            if (n < 0) continue;

            if (n < (int)sizeof(struct ayiyahdr))
            {
                ayiya_log(LOG_WARNING, writer_name, &ci, cl, "Received packet is too short");
                continue;
            }

            if (	s->ayh.ayh_idlen != 4 ||
                    s->ayh.ayh_idtype != ayiya_id_integer ||
                            s->ayh.ayh_siglen != 5 ||
                                    s->ayh.ayh_hshmeth != ayiya_hash_sha1 ||
                                            s->ayh.ayh_autmeth != ayiya_auth_sharedsecret ||
                                                    (s->ayh.ayh_nextheader != IPPROTO_IPV6 &&
                                                            s->ayh.ayh_nextheader != IPPROTO_NONE) ||
                                                    (s->ayh.ayh_opcode != ayiya_op_forward &&
                                                            s->ayh.ayh_opcode != ayiya_op_echo_request &&
                                                                    s->ayh.ayh_opcode != ayiya_op_echo_request_forward))
            {
			/* Invalid AYIYA packet */
                ayiya_log(LOG_ERR, writer_name, &ci, cl, "Dropping invalid AYIYA packet\n");
                ayiya_log(LOG_ERR, writer_name, &ci, cl, "idlen:   %u != %u\n", s->ayh.ayh_idlen, 4);
                ayiya_log(LOG_ERR, writer_name, &ci, cl, "idtype:  %u != %u\n", s->ayh.ayh_idtype, ayiya_id_integer);
                ayiya_log(LOG_ERR, writer_name, &ci, cl, "siglen:  %u != %u\n", s->ayh.ayh_siglen, 5);
                ayiya_log(LOG_ERR, writer_name, &ci, cl, "hshmeth: %u != %u\n", s->ayh.ayh_hshmeth, ayiya_hash_sha1);
                ayiya_log(LOG_ERR, writer_name, &ci, cl, "autmeth: %u != %u\n", s->ayh.ayh_autmeth, ayiya_auth_sharedsecret);
                ayiya_log(LOG_ERR, writer_name, &ci, cl, "nexth  : %u != %u || %u\n", s->ayh.ayh_nextheader, IPPROTO_IPV6, IPPROTO_NONE);
                ayiya_log(LOG_ERR, writer_name, &ci, cl, "opcode : %u != %u || %u || %u\n", s->ayh.ayh_opcode, ayiya_op_forward, ayiya_op_echo_request, ayiya_op_echo_request_forward);
                continue;
            }

            if (memcmp(&s->identity, &ayiya_ipv6_pop, sizeof(s->identity)) != 0)
            {
                memset(buf, 0, sizeof(buf));
                inet_ntop(AF_INET6, &s->identity, (char *)&buf, sizeof(buf));
                ayiya_log(LOG_WARNING, writer_name, &ci, cl, "Received packet from a wrong identity \"%s\"\n", buf);
                continue;
            }

		/* Verify the epochtime */
            i = tic_checktime(ntohl(s->ayh.ayh_epochtime));
            if (i != 0)
            {
                memset(buf, 0, sizeof(buf));
                inet_ntop(AF_INET6, &s->identity, (char *)&buf, sizeof(buf));
                ayiya_log(LOG_WARNING, writer_name, &ci, cl, "Time is %d seconds off for %s\n", i, buf);
                continue;
            }

		/* How long is the payload? */
            payloadlen = n - (sizeof(*s) - sizeof(s->payload));

		/* Save their hash */
            memcpy(&their_hash, &s->hash, sizeof(their_hash));

		/* Copy in our SHA1 hash */
            memcpy(&s->hash, &ayiya_hash, sizeof(s->hash));

		/* Generate a SHA1 of the header + identity + shared secret */
            SHA1_Init(&sha1);
		/* Hash the Packet */
            SHA1_Update(&sha1, (sha1_byte *)s, n);
		/* Store the hash */
            SHA1_Final(our_hash, &sha1);

            memcpy(&s->hash, &our_hash, sizeof(s->hash));

		/* Compare the SHA1's */
            if (memcmp(&their_hash, &our_hash, sizeof(their_hash)) != 0)
            {
                ayiya_log(LOG_WARNING, writer_name, &ci, cl, "Incorrect Hash received\n");
                continue;
            }

            if (s->ayh.ayh_nextheader == IPPROTO_IPV6)
            {
			/* Verify that this is really IPv6 */
                if (s->payload[0] >> 4 != 6)
                {
                    ayiya_log(LOG_ERR, writer_name, &ci, cl, "Received packet didn't start with a 6, thus is not IPv6\n");
                    continue;
                }

			/* Forward the packet to the kernel */
                tun_write(s->payload, payloadlen);
            }
        }

	/* Tun/TAP device is not running anymore */
        g_aiccu->tunrunning = false;

        #ifndef _WIN32
        return NULL;
        #else
        return 0;
        #endif
    }

    /* Construct a beat and send it outwards */
    void ayiya_beat(void)
    {
        SHA_CTX			sha1;
        sha1_byte		hash[SHA1_DIGEST_LENGTH];
        struct sockaddr_in	target;
        struct pseudo_ayh	s;
        int			lenout, n;

	/* We tunnel over IPv4 */
        memcpy(&target.sin_addr, &ayiya_ipv4_pop, sizeof(target.sin_addr));
        target.sin_family	= AF_INET;
        target.sin_port		= htons(atoi(AYIYA_PORT));

	/* Prefill some standard AYIYA values */
        memset(&s, 0, sizeof(s));
        s.ayh.ayh_idlen		= 4;			/* 2^4 = 16 bytes = 128 bits (IPv6 address) */
        s.ayh.ayh_idtype	= ayiya_id_integer;
        s.ayh.ayh_siglen	= 5;			/* 5*4 = 20 bytes = 160 bits (SHA1) */
        s.ayh.ayh_hshmeth	= ayiya_hash_sha1;
        s.ayh.ayh_autmeth	= ayiya_auth_sharedsecret;
        s.ayh.ayh_opcode	= ayiya_op_noop;
        s.ayh.ayh_nextheader	= IPPROTO_NONE;

	/* Our IPv6 side of this tunnel */
        memcpy(&s.identity, &ayiya_ipv6_local, sizeof(s.identity));

	/* No Payload */

	/* Fill in the current time */
        s.ayh.ayh_epochtime = htonl((u_long)time(NULL));

	/* Our IPv6 side of this tunnel */
        memcpy(&s.identity, &ayiya_ipv6_local, sizeof(s.identity));

	/*
	 * The hash of the shared secret needs to be in the
	 * spot where we later put the complete hash
	 */
        memcpy(&s.hash, ayiya_hash, sizeof(s.hash));

	/* Generate a SHA1 */
        SHA1_Init(&sha1);
	/* Hash the complete AYIYA packet */
        SHA1_Update(&sha1, (sha1_byte *)&s, sizeof(s)-sizeof(s.payload));
	/* Store the hash in the packets hash */
        SHA1_Final(hash, &sha1);

	/* Store the hash in the actual packet */
        memcpy(&s.hash, &hash, sizeof(s.hash));

	/* Send it onto the network */
        n = sizeof(s)-sizeof(s.payload);
        #if defined(_FREEBSD) || defined(_DFBSD) || defined(_OPENBSD) || defined(_DARWIN) || defined(_NETBSD)
        lenout = send(ayiya_socket->socket, (const char *)&s, (unsigned int)n, 0);
        #else
        lenout = sendto(ayiya_socket->socket, (const char *)&s, (unsigned int)n, 0, (struct sockaddr *)&target, sizeof(target));
        #endif
        if (lenout < 0)
        {
            ayiya_log(LOG_ERR, beat_name, NULL, 0, "Error (%d) while sending %u bytes sent to network: %s (%d)\n", lenout, n, strerror(errno), errno);
        }
        else if (n != lenout)
        {
            ayiya_log(LOG_ERR, beat_name, NULL, 0, "Only %u of %u bytes sent to network: %s (%d)\n", lenout, n, strerror(errno), errno);
        }
    }

    bool ayiya(struct TIC_Tunnel *hTunnel)
    {

}
