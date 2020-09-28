package com.mobius.software.iot.dal.test.dtls;

/**
 * Mobius Software LTD
 * Copyright 2018, Mobius Software LTD
 *
 * This is free software; you can redistribute it and/or modify it
 * under the terms of the GNU Lesser General Public License as
 * published by the Free Software Foundation; either version 2.1 of
 * the License, or (at your option) any later version.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE. See the GNU
 * Lesser General Public License for more details.
 *
 * You should have received a copy of the GNU Lesser General Public
 * License along with this software; if not, write to the Free
 * Software Foundation, Inc., 51 Franklin St, Fifth Floor, Boston, MA
 * 02110-1301 USA, or see the FSF site: http://www.fsf.org.
 */

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.SocketAddress;
import java.security.KeyStore;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.concurrent.atomic.AtomicInteger;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.bouncycastle.crypto.tls.Certificate;
import org.bouncycastle.crypto.tls.ProtocolVersion;

import com.mobius.software.iot.dal.crypto.AsyncDtlsClient;
import com.mobius.software.iot.dal.crypto.AsyncDtlsClientHandler;
import com.mobius.software.iot.dal.crypto.AsyncDtlsClientProtocol;
import com.mobius.software.iot.dal.crypto.DtlsStateHandler;
import com.mobius.software.iot.dal.crypto.MessageType;

import io.netty.bootstrap.Bootstrap;
import io.netty.buffer.ByteBuf;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.nio.NioDatagramChannel;

public class DtlsClient implements MessageHandlerInterface,DtlsStateHandler
{
	private static final Log logger = LogFactory.getLog(DtlsClient.class);

	private AsyncDtlsClientProtocol protocol=null;
	private SecureRandom SECURE_RANDOM = new SecureRandom();
			
	private NioEventLoopGroup clientGroup;
	private Bootstrap clientBootstrap = new Bootstrap();
	private Channel channel;
	
	private String host;
	private String remoteHost;
	private Integer remotePort;
	private Integer localPort=0;
	private KeyStore keystore;
	private String keystorePassword;
	
	private AtomicInteger messagesCount=new AtomicInteger(0);
	private ArrayList<String> messages=new ArrayList<String>();
	
	private TestHandshakeHandler handshakeHandler=new TestHandshakeHandler();
	
	public DtlsClient(String host,String remoteHost,Integer remotePort,KeyStore keystore,String keystorePassword)
	{
		this.host=host;
		this.remoteHost=remoteHost;
		this.remotePort=remotePort;
		this.keystore=keystore;
		this.keystorePassword=keystorePassword;
	}
	
	public DtlsClient(String host,String remoteHost,Integer remotePort,Integer localPort,KeyStore keystore,String keystorePassword)
	{
		this.host=host;
		this.remoteHost=remoteHost;
		this.remotePort=remotePort;
		this.localPort=localPort;
		this.keystore=keystore;
		this.keystorePassword=keystorePassword;
	}
	
	public boolean establishConnection()
	{
		clientGroup = new NioEventLoopGroup(4);
		clientBootstrap.group(clientGroup);
		clientBootstrap.channel(NioDatagramChannel.class);
		clientBootstrap.option(ChannelOption.TCP_NODELAY, true);
		clientBootstrap.option(ChannelOption.SO_KEEPALIVE, true);
		
		final InetSocketAddress remoteAddress = new InetSocketAddress(remoteHost, remotePort);
		final InetSocketAddress localAddress = new InetSocketAddress(host, localPort);
		final DtlsClient client=this;
		
		clientBootstrap.handler(new ChannelInitializer<NioDatagramChannel>()
		{
			@Override
			protected void initChannel(NioDatagramChannel socketChannel) throws Exception
			{
				protocol=new AsyncDtlsClientProtocol(new AsyncDtlsClient(keystore, keystorePassword,null),SECURE_RANDOM, socketChannel,handshakeHandler,client, remoteAddress, true,ProtocolVersion.DTLSv12);
				socketChannel.pipeline().addLast(new AsyncDtlsClientHandler(protocol,client));
				socketChannel.pipeline().addLast(new DummyMessageHandler(client));
			}
		});

		try
		{
			clientBootstrap.remoteAddress(remoteAddress);
			clientBootstrap.localAddress(localAddress);
			ChannelFuture future = clientBootstrap.connect().sync().awaitUninterruptibly();
			channel = future.channel();
			try
			{
				protocol.initHandshake(null);
			}
			catch(IOException ex)
			{
				logger.error("An error occured while initializing handshake",ex);
			}
		}
		catch (InterruptedException e)
		{			
		}

		return true;
	}
	
	public void sendPacket(ByteBuf buffer) throws IOException
	{
		protocol.sendPacket(buffer);
	}
	
	public void shutdown()
	{
		channel.close();
		clientGroup.shutdownGracefully() ;
	}
	
	@Override
	public void messageReceived(String message) 
	{
		messages.add(message);
		messagesCount.incrementAndGet();
	}
	
	public Integer getMessagesReceived()
	{
		return messagesCount.get();
	}

	@Override
	public Integer handshakeMessagesReceived(MessageType messageType) 
	{
		return handshakeHandler.getCount(messageType);
	}
	
	public SocketAddress getLocalAddress()
	{
		return channel.localAddress();
	}

	public Certificate getServerCertificate()
	{
		return protocol.getServerCertificate();
	}
	
	@Override
	public void handshakeStarted(InetSocketAddress address,Channel channel) 
	{
		logger.info("Handshake started for:" + address);
	}

	@Override
	public void handshakeCompleted(InetSocketAddress address,Channel channel) 
	{
		logger.info("Handshake completed for:" + address);
	}

	@Override
	public void errorOccured(InetSocketAddress address,Channel channel) 
	{
		if (channel.isOpen())
			channel.close();
	}

	@Override
	public String getMessage(Integer index) 
	{
		return messages.get(index);
	}
}