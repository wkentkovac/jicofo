/*
 * Jicofo, the Jitsi Conference Focus.
 *
 * Distributable under LGPL license.
 * See terms of license at gnu.org.
 */
package org.jitsi.jicofo.auth;

import net.java.sip.communicator.util.*;
import net.java.sip.communicator.util.Logger;

import org.eclipse.jetty.server.*;
import org.eclipse.jetty.server.handler.*;

import org.jitsi.util.*;

import org.osgi.framework.*;

import javax.servlet.*;
import javax.servlet.http.*;
import java.io.*;
import java.util.*;

/**
 * Implements a Jetty <tt>Handler</tt> which is meant to be used as a servlet
 * that runs on "server path" secured by Shibboleth authentication. It should
 * be integrated with Apache through AJP connection which will provide valid
 * Shibboleth attributes to servlet session. Attributes can be used to obtain
 * user's identity provided by Shibboleth SP.
 * <br/><br/>
 * See for more info:
 * https://wiki.shibboleth.net/confluence/display/SHIB2/NativeSPJavaInstall
 *
 * @author Pawel Domas
 */
class ShibbolethHandler
    extends AbstractHandler
{
    /**
     * The logger instance used by Shibboleth handler.
     */
    private static final Logger logger
            = Logger.getLogger(ShibbolethHandler.class);

    /**
     * The <tt>BundleContext</tt> within which this instance is initialized.
     */
    private final BundleContext bundleContext;

    /**
     * Initializes a new <tt>ShibbolethHandler</tt> instance within a specific
     * <tt>BundleContext</tt>.
     *
     * @param bundleContext the <tt>BundleContext</tt> within which the new
     * instance is to be initialized
     */
    public ShibbolethHandler(BundleContext bundleContext)
    {
        this.bundleContext = bundleContext;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void handle(
            String target,
            Request baseRequest,
            HttpServletRequest request,
            HttpServletResponse response)
            throws IOException,
            ServletException
    {
        try
        {
            doHandle(target, baseRequest, request, response);
        }
        catch(Exception e)
        {
            logger.error(e, e);

            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            baseRequest.setHandled(true);
        }
    }

    private void doHandle(
        String target,
        Request baseRequest,
        HttpServletRequest request,
        HttpServletResponse response)
        throws IOException,
               ServletException
    {

        String token = request.getParameter("token");
        if (StringUtils.isNullOrEmpty(token))
        {
            response.setStatus(HttpServletResponse.SC_BAD_REQUEST);
            baseRequest.setHandled(true);
            return;
        }

        // Check 'mail' attribute which should be set by Shibboleth through AJP
        String email = (String) request.getAttribute("mail");
        if (StringUtils.isNullOrEmpty(email))
        {
            response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
            baseRequest.setHandled(true);
            return;
        }

        // User authenticated
        AuthAuthority authAuthority
            = ServiceUtils.getService(bundleContext, AuthAuthority.class);
        if (!authAuthority.authenticateUser(token, email))
        {
            response.setStatus(HttpServletResponse.SC_NOT_ACCEPTABLE);
            baseRequest.setHandled(true);
            return;
        }

        PrintWriter responseWriter = response.getWriter();

        String displayName = (String) request.getAttribute("displayName");
        responseWriter.println("<h1>Hello " + displayName);
        responseWriter.println(
            " !<h1/><h2><a href=\"#\" onclick=\"self.close();\">Close</a>" +
                    " this window to start the conference.<h2/>");

        if (logger.isDebugEnabled())
        {
            responseWriter.println("<br/>Debug:");
            Enumeration<String> attributes = request.getAttributeNames();
            while (attributes.hasMoreElements())
            {
                String attributeName = attributes.nextElement();
                responseWriter.print("<br/>" + attributeName + ": ");
                responseWriter.print(
                        String.valueOf(request.getAttribute(attributeName)));
            }

            responseWriter.println("<br/>token: " + token);
        }

        response.setStatus(HttpServletResponse.SC_OK);
        baseRequest.setHandled(true);
    }
}
