/*
 * DO NOT ALTER OR REMOVE COPYRIGHT NOTICES OR THIS HEADER.
 *
 * Copyright (c) 1997-2013 Oracle and/or its affiliates. All rights reserved.
 *
 * The contents of this file are subject to the terms of either the GNU
 * General Public License Version 2 only ("GPL") or the Common Development
 * and Distribution License("CDDL") (collectively, the "License").  You
 * may not use this file except in compliance with the License.  You can
 * obtain a copy of the License at
 * https://glassfish.dev.java.net/public/CDDL+GPL_1_1.html
 * or packager/legal/LICENSE.txt.  See the License for the specific
 * language governing permissions and limitations under the License.
 *
 * When distributing the software, include this License Header Notice in each
 * file and include the License file at packager/legal/LICENSE.txt.
 *
 * GPL Classpath Exception:
 * Oracle designates this particular file as subject to the "Classpath"
 * exception as provided by Oracle in the GPL Version 2 section of the License
 * file that accompanied this code.
 *
 * Modifications:
 * If applicable, add the following below the License Header, with the fields
 * enclosed by brackets [] replaced by your own identifying information:
 * "Portions Copyright [year] [name of copyright owner]"
 *
 * Contributor(s):
 * If you wish your version of this file to be governed by only the CDDL or
 * only the GPL Version 2, indicate your decision by adding "[Contributor]
 * elects to include this software in this distribution under the [CDDL or GPL
 * Version 2] license."  If you don't indicate a single choice of license, a
 * recipient has the option to distribute your version of this file under
 * either the CDDL, the GPL Version 2 or to extend the choice of license to
 * its licensees as provided above.  However, if you add GPL Version 2 code
 * and therefore, elected the GPL Version 2 license, then the option applies
 * only if the new code is made subject to such option by the copyright
 * holder.
 */
// Portions Copyright [2015] [C2B2 Consulting Limited]

package org.glassfish.jdbc.deployer;

import static org.glassfish.deployment.common.JavaEEResourceType.DSDPOOL;

import java.beans.PropertyVetoException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import javax.inject.Inject;
import javax.inject.Provider;
import javax.naming.NamingException;

import org.glassfish.deployment.common.Descriptor;
import org.glassfish.deployment.common.JavaEEResourceType;
import org.glassfish.deployment.common.RootDeploymentDescriptor;
import org.glassfish.javaee.services.CommonResourceProxy;
import org.glassfish.jdbc.config.JdbcConnectionPool;
import org.glassfish.jdbc.config.JdbcResource;
import org.glassfish.jdbc.util.LoggerFactory;
import org.glassfish.resourcebase.resources.api.ResourceConflictException;
import org.glassfish.resourcebase.resources.api.ResourceConstants;
import org.glassfish.resourcebase.resources.api.ResourceDeployer;
import org.glassfish.resourcebase.resources.api.ResourceDeployerInfo;
import org.glassfish.resourcebase.resources.api.ResourceInfo;
import org.glassfish.resourcebase.resources.naming.ResourceNamingService;
import org.glassfish.resourcebase.resources.util.ResourceManagerFactory;
import org.jvnet.hk2.annotations.Service;
import org.jvnet.hk2.config.ConfigBeanProxy;
import org.jvnet.hk2.config.TransactionFailure;
import org.jvnet.hk2.config.types.Property;

import com.sun.appserv.connectors.internal.api.ConnectorConstants;
import com.sun.appserv.connectors.internal.api.ConnectorsUtil;
import com.sun.enterprise.config.serverbeans.Resource;
import com.sun.enterprise.config.serverbeans.Resources;
import com.sun.enterprise.deployment.BundleDescriptor;
import com.sun.enterprise.deployment.DataSourceDefinitionDescriptor;
import com.sun.enterprise.deployment.EjbBundleDescriptor;
import com.sun.enterprise.deployment.EjbDescriptor;
import com.sun.enterprise.deployment.EjbInterceptor;
import com.sun.enterprise.deployment.JndiNameEnvironment;
import com.sun.enterprise.deployment.ManagedBeanDescriptor;


/**
 * @author Jagadish Ramu
 */
@Service
@ResourceDeployerInfo(DataSourceDefinitionDescriptor.class)
public class DataSourceDefinitionDeployer implements ResourceDeployer {

    @Inject
    private Provider<ResourceManagerFactory> resourceManagerFactoryProvider;

    @Inject
    private Provider<CommonResourceProxy> dataSourceDefinitionProxyProvider;

    @Inject
    private Provider<ResourceNamingService> resourceNamingServiceProvider;

    private static Logger _logger = LoggerFactory.getLogger(DataSourceDefinitionDeployer.class);

    @Override
    public void deployResource(Object resource, String applicationName, String moduleName) throws Exception {
        //TODO ASR
    }
    @Override
    public void deployResource(Object resource) throws Exception {

        final DataSourceDefinitionDescriptor desc = (DataSourceDefinitionDescriptor) resource;
        String poolName = ConnectorsUtil.deriveResourceName(desc.getResourceId(), desc.getName(), DSDPOOL);
        String resourceName = ConnectorsUtil.deriveResourceName(desc.getResourceId(), desc.getName(), desc.getResourceType());
                //desc.getName();

        if(_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "DataSourceDefinitionDeployer.deployResource() : pool-name ["+poolName+"], " +
                    " resource-name ["+resourceName+"]");
        }

        JdbcConnectionPool jdbcCp = new MyJdbcConnectionPool(desc, poolName);

        //deploy pool
        getDeployer(jdbcCp).deployResource(jdbcCp);

        //deploy resource
        JdbcResource jdbcResource = new MyJdbcResource(poolName, resourceName);
        getDeployer(jdbcResource).deployResource(jdbcResource);
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public boolean canDeploy(boolean postApplicationDeployment, Collection<Resource> allResources, Resource resource){
        if(handles(resource)){
            if(!postApplicationDeployment){
                return true;
            }
        }
        return false;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void validatePreservedResource(com.sun.enterprise.config.serverbeans.Application oldApp,
                                          com.sun.enterprise.config.serverbeans.Application newApp, Resource resource,
                                  Resources allResources)
    throws ResourceConflictException {
        //do nothing.
    }


    private ResourceDeployer getDeployer(Object resource) {
        return resourceManagerFactoryProvider.get().getResourceDeployer(resource);
    }

    private DataSourceProperty convertProperty(String name, String value) {
        return new DataSourceProperty(name, value);
    }


    public void registerDataSourceDefinitions(com.sun.enterprise.deployment.Application application) {
        String appName = application.getAppName();
        Set<BundleDescriptor> bundles = application.getBundleDescriptors();
        for (BundleDescriptor bundle : bundles) {
            registerDataSourceDefinitions(appName, bundle);
            Collection<RootDeploymentDescriptor> dds = bundle.getExtensionsDescriptors();
            if(dds != null){
                for(RootDeploymentDescriptor dd : dds){
                    registerDataSourceDefinitions(appName, dd);
                }
            }
        }
    }

    private void registerDataSourceDefinitions(String appName, Descriptor descriptor) {

        if (descriptor instanceof JndiNameEnvironment) {
            JndiNameEnvironment env = (JndiNameEnvironment) descriptor;
            for (Descriptor dsd : env.getResourceDescriptors(JavaEEResourceType.DSD)) {
                registerDSDReferredByApplication(appName,(DataSourceDefinitionDescriptor) dsd);
            }
        }

        //ejb descriptor
        if (descriptor instanceof EjbBundleDescriptor) {
            EjbBundleDescriptor ejbDesc = (EjbBundleDescriptor) descriptor;
            Set<? extends EjbDescriptor> ejbDescriptors = ejbDesc.getEjbs();
            for (EjbDescriptor ejbDescriptor : ejbDescriptors) {
                for (Descriptor dsd : ejbDescriptor.getResourceDescriptors(JavaEEResourceType.DSD)) {
                    registerDSDReferredByApplication(appName,(DataSourceDefinitionDescriptor) dsd);
                }
            }
            //ejb interceptors
            Set<EjbInterceptor> ejbInterceptors = ejbDesc.getInterceptors();
            for (EjbInterceptor ejbInterceptor : ejbInterceptors) {
                for (Descriptor dsd : ejbInterceptor.getResourceDescriptors(JavaEEResourceType.DSD)) {
                    registerDSDReferredByApplication(appName,(DataSourceDefinitionDescriptor) dsd);
                }
            }
        }

        if(descriptor instanceof BundleDescriptor){
            // managed bean descriptors
            Set<ManagedBeanDescriptor> managedBeanDescriptors = ((BundleDescriptor)descriptor).getManagedBeans();
            for (ManagedBeanDescriptor mbd : managedBeanDescriptors) {
                for (Descriptor dsd : mbd.getResourceDescriptors(JavaEEResourceType.DSD)) {
                    registerDSDReferredByApplication(appName, (DataSourceDefinitionDescriptor)dsd);
                }
            }
        }
    }


    private void unregisterDSDReferredByApplication(String appName, DataSourceDefinitionDescriptor dsd){
        try{
            if(dsd.isDeployed()){
                undeployResource(dsd);
                
                // unbind from JNDI              
                ResourceNamingService resourceNamingService = resourceNamingServiceProvider.get();
                String dsdName = dsd.getName();

                if(dsdName.startsWith(ResourceConstants.JAVA_GLOBAL_SCOPE_PREFIX)
                        /*|| next.getName().startsWith("java:module/")*/
                        || dsdName.startsWith(ResourceConstants.JAVA_APP_SCOPE_PREFIX)){
                    ResourceInfo resourceInfo = new ResourceInfo(dsdName, appName, null);
                    try {
                        resourceNamingService.unpublishObject(resourceInfo, dsdName);
                        dsd.setDeployed(false);
                    } catch (NamingException e) {
                        Object params[] = new Object[]{appName, dsdName, e};
                        _logger.log(Level.WARNING, "dsd.unregistration.failed", params);
                    }
                }
            }
        }catch(Exception e){
            _logger.log(Level.WARNING, "exception while unregistering DSD [ "+dsd.getName()+" ]", e);
        }
    }

    public void unRegisterDataSourceDefinitions(com.sun.enterprise.deployment.Application application) {
        Set<BundleDescriptor> bundles = application.getBundleDescriptors();
        for (BundleDescriptor bundle : bundles) {
            unRegisterDataSourceDefinitions(application.getName(),bundle);
            Collection<RootDeploymentDescriptor> dds = bundle.getExtensionsDescriptors();
            if(dds != null){
                for(RootDeploymentDescriptor dd : dds){
                    unRegisterDataSourceDefinitions(application.getName(), dd);
                }
            }
        }
    }

    private void unRegisterDataSourceDefinitions(String appName, Descriptor descriptor) {
        if (descriptor instanceof JndiNameEnvironment) {
            JndiNameEnvironment env = (JndiNameEnvironment) descriptor;
            for (Descriptor dsd : env.getResourceDescriptors(JavaEEResourceType.DSD)) {
                unregisterDSDReferredByApplication(appName, (DataSourceDefinitionDescriptor)dsd);
            }
        }

        //ejb descriptor
        if (descriptor instanceof EjbBundleDescriptor) {
            EjbBundleDescriptor ejbDesc = (EjbBundleDescriptor) descriptor;
            Set<? extends EjbDescriptor> ejbDescriptors = ejbDesc.getEjbs();
            for (EjbDescriptor ejbDescriptor : ejbDescriptors) {
                for (Descriptor dsd : ejbDescriptor.getResourceDescriptors(JavaEEResourceType.DSD)) {
                    unregisterDSDReferredByApplication(appName, (DataSourceDefinitionDescriptor)dsd);
                }
            }
            //ejb interceptors
            Set<EjbInterceptor> ejbInterceptors = ejbDesc.getInterceptors();
            for (EjbInterceptor ejbInterceptor : ejbInterceptors) {
                for (Descriptor dsd : ejbInterceptor.getResourceDescriptors(JavaEEResourceType.DSD)) {
                    unregisterDSDReferredByApplication(appName, (DataSourceDefinitionDescriptor)dsd);
                }
            }
        }

        // managed bean descriptors
        if(descriptor instanceof BundleDescriptor){
            Set<ManagedBeanDescriptor> managedBeanDescriptors = ((BundleDescriptor)descriptor).getManagedBeans();
            for (ManagedBeanDescriptor mbd : managedBeanDescriptors) {
                for (Descriptor dsd : mbd.getResourceDescriptors(JavaEEResourceType.DSD)) {
                    unregisterDSDReferredByApplication(appName, (DataSourceDefinitionDescriptor)dsd);
                }
            }
        }
    }

    private void registerDSDReferredByApplication(String appName,
                                            DataSourceDefinitionDescriptor dsd) {
        // It is possible that JPA might call this method multiple times in a single deployment,
        // when there are multiple PUs eg: one PU in each of war, ejb-jar. Make sure that
        // DSD is bound to JNDI only when it is not already deployed.
        if(!dsd.isDeployed()){
            CommonResourceProxy proxy = dataSourceDefinitionProxyProvider.get();
            ResourceNamingService resourceNamingService = resourceNamingServiceProvider.get();
            proxy.setDescriptor(dsd);

            //String appName = application.getAppName();
            String dsdName = dsd.getName();
            if(dsdName.startsWith(ResourceConstants.JAVA_APP_SCOPE_PREFIX)){
                dsd.setResourceId(appName);
            }

            if(dsdName.startsWith(ResourceConstants.JAVA_GLOBAL_SCOPE_PREFIX)
                    /*|| next.getName().startsWith("java:module/")*/
                    || dsdName.startsWith(ResourceConstants.JAVA_APP_SCOPE_PREFIX)){
                ResourceInfo resourceInfo = new ResourceInfo(dsdName, appName, null);
                try {
                    resourceNamingService.publishObject(resourceInfo, proxy, true);
                    dsd.setDeployed(true);
                } catch (NamingException e) {
                    Object params[] = new Object[]{appName, dsdName, e};
                    _logger.log(Level.WARNING, "dsd.registration.failed", params);
                }
            }
        }
    }

    @Override
    public void undeployResource(Object resource, String applicationName, String moduleName) throws Exception {
        //TODO ASR
    }

    @Override
    public void undeployResource(Object resource) throws Exception {

        final DataSourceDefinitionDescriptor desc = (DataSourceDefinitionDescriptor) resource;

        String poolName = ConnectorsUtil.deriveResourceName(desc.getResourceId(), desc.getName(), DSDPOOL);
        String resourceName = ConnectorsUtil.deriveResourceName(desc.getResourceId(), desc.getName(),desc.getResourceType());

        if(_logger.isLoggable(Level.FINE)) {
            _logger.log(Level.FINE, "DataSourceDefinitionDeployer.undeployResource() : pool-name ["+poolName+"], " +
                    " resource-name ["+resourceName+"]");
        }

        //undeploy resource
        JdbcResource jdbcResource = new MyJdbcResource(poolName, resourceName);
        getDeployer(jdbcResource).undeployResource(jdbcResource);

        //undeploy pool
        JdbcConnectionPool jdbcCp = new MyJdbcConnectionPool(desc, poolName);
        getDeployer(jdbcCp).undeployResource(jdbcCp);

        desc.setDeployed(false);
    }

    @Override
    public void redeployResource(Object resource) throws Exception {
        throw new UnsupportedOperationException("redeploy() not supported for datasource-definition type");
    }

    @Override
    public void enableResource(Object resource) throws Exception {
        throw new UnsupportedOperationException("enable() not supported for datasource-definition type");
    }

    @Override
    public void disableResource(Object resource) throws Exception {
        throw new UnsupportedOperationException("disable() not supported for datasource-definition type");
    }

    @Override
    public boolean handles(Object resource) {
        return resource instanceof DataSourceDefinitionDescriptor;
    }

    /**
     * @inheritDoc
     */
    @Override
    public boolean supportsDynamicReconfiguration() {
        return false;
    }

    /**
     * @inheritDoc
     */
    @Override
    public Class[] getProxyClassesForDynamicReconfiguration() {
        return new Class[0];
    }

    abstract class FakeConfigBean implements ConfigBeanProxy {
        @Override
        public ConfigBeanProxy deepCopy(ConfigBeanProxy parent) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ConfigBeanProxy getParent() {
            return null;
        }

        @Override
        public <T extends ConfigBeanProxy> T getParent(Class<T> tClass) {
            return null;
        }

        @Override
        public <T extends ConfigBeanProxy> T createChild(Class<T> tClass) throws TransactionFailure {
            return null;
        }
    }

    class DataSourceProperty extends FakeConfigBean implements Property {

        private String name;
        private String value;
        private String description;

        DataSourceProperty(String name, String value) {
            this.name = name;
            this.value = value;
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String value) throws PropertyVetoException {
            this.name = value;
        }

        @Override
        public String getValue() {
            return value;
        }

        @Override
        public void setValue(String value) throws PropertyVetoException {
            this.value = value;
        }

        @Override
        public String getDescription() {
            return description;
        }

        @Override
        public void setDescription(String value) throws PropertyVetoException {
            this.description = value;
        }

        public void injectedInto(Object o) {
            //do nothing
        }
    }

    class MyJdbcResource extends FakeConfigBean implements JdbcResource {

        private String poolName;
        private String jndiName;

        MyJdbcResource(String poolName, String jndiName) {
            this.poolName = poolName;
            this.jndiName = jndiName;
        }

        @Override
        public String getPoolName() {
            return poolName;
        }

        @Override
        public void setPoolName(String value) throws PropertyVetoException {
            this.poolName = value;
        }

        @Override
        public String getObjectType() {
            return null;
        }

        @Override
        public void setObjectType(String value) throws PropertyVetoException {
        }

        @Override
        public String getIdentity() {
            return jndiName;
        }

        @Override
        public String getEnabled() {
            return String.valueOf(true);
        }

        @Override
        public void setEnabled(String value) throws PropertyVetoException {
        }

        @Override
        public String getDescription() {
            return null;
        }

        @Override
        public void setDescription(String value) throws PropertyVetoException {
        }

        @Override
        public List<Property> getProperty() {
            return null;
        }

        @Override
        public Property getProperty(String name) {
            return null;
        }

        @Override
        public String getPropertyValue(String name) {
            return null;
        }

        @Override
        public String getPropertyValue(String name, String defaultValue) {
            return null;
        }

        public void injectedInto(Object o) {
        }

        @Override
        public String getJndiName() {
            return jndiName;
        }

        @Override
        public void setJndiName(String value) throws PropertyVetoException {
            this.jndiName = value;
        }

        @Override
        public String getDeploymentOrder() {
            return null;
        }

        @Override
        public void setDeploymentOrder(String value) {
            //do nothing
        }
    }

    class MyJdbcConnectionPool extends FakeConfigBean implements JdbcConnectionPool {

        private DataSourceDefinitionDescriptor desc;
        private String name;

        public MyJdbcConnectionPool(DataSourceDefinitionDescriptor desc, String name) {
            this.desc = desc;
            this.name = name;
        }

        @Override
        public String getDatasourceClassname() {
            if(!getResType().equals(ConnectorConstants.JAVA_SQL_DRIVER)){
                return desc.getClassName();
            }
            return null;
        }

        @Override
        public void setDatasourceClassname(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getResType() {
            String type = ConnectorConstants.JAVAX_SQL_DATASOURCE;
            try {
                Class clz = Thread.currentThread().getContextClassLoader().loadClass(desc.getClassName());
                 if (javax.sql.XADataSource.class.isAssignableFrom(clz)) {
                    type = ConnectorConstants.JAVAX_SQL_XA_DATASOURCE;
                } else if (javax.sql.ConnectionPoolDataSource.class.isAssignableFrom(clz)) {
                    type = ConnectorConstants.JAVAX_SQL_CONNECTION_POOL_DATASOURCE;
                } else if (javax.sql.DataSource.class.isAssignableFrom(clz)){
                    type = ConnectorConstants.JAVAX_SQL_DATASOURCE;
                } else if(java.sql.Driver.class.isAssignableFrom(clz)){
                     type = ConnectorConstants.JAVA_SQL_DRIVER;
                 }
            } catch (ClassNotFoundException e) {
                if(_logger.isLoggable(Level.FINEST)) {
                    _logger.log(Level.FINEST, "Unable to load class [ " + desc.getClassName() + " ] to " +
                        "determine its res-type, defaulting to ["+ConnectorConstants.JAVAX_SQL_DATASOURCE+"]");
                }
                // ignore and default to "javax.sql.DataSource"
            }
            return type;
        }

        @Override
        public void setResType(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getObjectType() {
            return null;  //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public void setObjectType(String value) throws PropertyVetoException {
            //To change body of implemented methods use File | Settings | File Templates.
        }

        @Override
        public String getIdentity() {
            return name;
        }

        @Override
        public String getSteadyPoolSize() {
            int minPoolSize = desc.getMinPoolSize();
            if (minPoolSize == -1) {
                minPoolSize = 8;
            }
            return String.valueOf(minPoolSize);
        }

        @Override
        public void setSteadyPoolSize(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getMaxPoolSize() {
            int maxPoolSize = desc.getMaxPoolSize();
            if (maxPoolSize == -1) {
                maxPoolSize = 32;
            }
            return String.valueOf(maxPoolSize);
        }

        @Override
        public void setMaxPoolSize(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getMaxWaitTimeInMillis() {
            return String.valueOf(60000);
        }

        @Override
        public void setMaxWaitTimeInMillis(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getPoolResizeQuantity() {
            return String.valueOf(2);
        }

        @Override
        public void setPoolResizeQuantity(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getIdleTimeoutInSeconds() {
            long maxIdleTime = desc.getMaxIdleTime();
            if (maxIdleTime == -1) {
                maxIdleTime = 300;
            }
            return String.valueOf(maxIdleTime);
        }

        @Override
        public void setIdleTimeoutInSeconds(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getTransactionIsolationLevel() {
            if (desc.getIsolationLevel() == -1) {
                return null;
            } else {
                return ConnectorsUtil.getTransactionIsolationInt(desc.getIsolationLevel());
            }
        }

        @Override
        public void setTransactionIsolationLevel(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getIsIsolationLevelGuaranteed() {
            return String.valueOf("true");
        }

        @Override
        public void setIsIsolationLevelGuaranteed(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getIsConnectionValidationRequired() {
            return String.valueOf("false");
        }

        @Override
        public void setIsConnectionValidationRequired(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getConnectionValidationMethod() {
            return null;
        }

        @Override
        public void setConnectionValidationMethod(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getValidationTableName() {
            return null;
        }

        @Override
        public void setValidationTableName(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getValidationClassname() {
            return null;
        }

        @Override
        public void setValidationClassname(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getFailAllConnections() {
            return String.valueOf("false");
        }

        @Override
        public void setFailAllConnections(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getNonTransactionalConnections() {
            return String.valueOf(!desc.isTransactional());
        }

        @Override
        public void setNonTransactionalConnections(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getAllowNonComponentCallers() {
            return String.valueOf("false");
        }

        @Override
        public void setAllowNonComponentCallers(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getValidateAtmostOncePeriodInSeconds() {
            return String.valueOf(0);
        }

        @Override
        public void setValidateAtmostOncePeriodInSeconds(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getConnectionLeakTimeoutInSeconds() {
            return String.valueOf(0);
        }

        @Override
        public void setConnectionLeakTimeoutInSeconds(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getConnectionLeakReclaim() {
            return String.valueOf(false);
        }

        @Override
        public void setConnectionLeakReclaim(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getConnectionCreationRetryAttempts() {
            return String.valueOf(0);
        }

        @Override
        public void setConnectionCreationRetryAttempts(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getConnectionCreationRetryIntervalInSeconds() {
            return String.valueOf(10);
        }

        @Override
        public void setConnectionCreationRetryIntervalInSeconds(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getStatementTimeoutInSeconds() {
            return String.valueOf(-1);
        }

        @Override
        public void setStatementTimeoutInSeconds(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getLazyConnectionEnlistment() {
            return String.valueOf(false);
        }

        @Override
        public void setLazyConnectionEnlistment(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getLazyConnectionAssociation() {
            return String.valueOf(false);
        }

        @Override
        public void setLazyConnectionAssociation(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getAssociateWithThread() {
            return String.valueOf(false);
        }

        @Override
        public void setAssociateWithThread(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getPooling() {
            return String.valueOf(true);
        }

        @Override
        public void setPooling(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getStatementCacheSize() {
            return String.valueOf(0);
        }

        @Override
        public void setStatementCacheSize(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getMatchConnections() {
            return String.valueOf(true);
        }

        @Override
        public void setMatchConnections(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getMaxConnectionUsageCount() {
            return String.valueOf(0);
        }

        @Override
        public void setMaxConnectionUsageCount(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getWrapJdbcObjects() {
            return String.valueOf(true);
        }

        @Override
        public void setWrapJdbcObjects(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getDescription() {
            return desc.getDescription();
        }

        @Override
        public void setDescription(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public List<Property> getProperty() {
            Properties p = desc.getProperties();
            List<Property> dataSourceProperties = new ArrayList<Property>();
            for (Map.Entry entry : p.entrySet()) {
                String key = (String) entry.getKey();
                String value = (String) entry.getValue();
                DataSourceProperty dp = convertProperty(key, value);
                dataSourceProperties.add(dp);
            }

            if (desc.getUser() != null) {
                DataSourceProperty property = convertProperty(("user"),
                        String.valueOf(desc.getUser()));
                dataSourceProperties.add(property);
            }

            if (desc.getPassword() != null) {
                DataSourceProperty property = convertProperty(("password"),
                        String.valueOf(desc.getPassword()));
                dataSourceProperties.add(property);
            }

            if (desc.getDatabaseName() != null) {
                DataSourceProperty property = convertProperty(("databaseName"),
                        String.valueOf(desc.getDatabaseName()));
                dataSourceProperties.add(property);
            }

            if (desc.getServerName() != null) {
                DataSourceProperty property = convertProperty(("serverName"),
                        String.valueOf(desc.getServerName()));
                dataSourceProperties.add(property);
            }

            if (desc.getPortNumber() != -1) {
                DataSourceProperty property = convertProperty(("portNumber"),
                        String.valueOf(desc.getPortNumber()));
                dataSourceProperties.add(property);
            }

            //process URL only when standard properties are not set
            if (desc.getUrl() != null && !isStandardPropertiesSet(desc)) {
                DataSourceProperty property = convertProperty(("url"),
                        String.valueOf(desc.getUrl()));
                dataSourceProperties.add(property);
            }

            if (desc.getLoginTimeout() != 0) {
                DataSourceProperty property = convertProperty(("loginTimeout"),
                        String.valueOf(desc.getLoginTimeout()));
                dataSourceProperties.add(property);
            }

            if (desc.getMaxStatements() != -1) {
                DataSourceProperty property = convertProperty(("maxStatements"),
                        String.valueOf(desc.getMaxStatements()));
                dataSourceProperties.add(property);
            }

            return dataSourceProperties;
        }

        private boolean isStandardPropertiesSet(DataSourceDefinitionDescriptor desc){
            boolean result = false;
            if(desc.getServerName() != null && desc.getDatabaseName() != null && desc.getPortNumber() != -1 ){
                result = true;
            }
            return result;
        }

        @Override
        public Property getProperty(String name) {
            Property result = null;
            String value = (String) desc.getProperties().get(name);
            if (value != null) {
                result = new DataSourceProperty(name, value);
            }
            return result;
        }

        @Override
        public String getPropertyValue(String name) {
            return (String) desc.getProperties().get(name);
        }

        @Override
        public String getPropertyValue(String name, String defaultValue) {
            String value = null;
            value = (String) desc.getProperties().get(name);
            if (value != null) {
                return value;
            } else {
                return defaultValue;
            }
        }

        public void injectedInto(Object o) {
            //do nothing
        }

        @Override
        public String getName() {
            return name;
        }

        @Override
        public void setName(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getSqlTraceListeners() {
            return null;
        }

        @Override
        public void setSqlTraceListeners(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getPing() {
            return String.valueOf(false);
        }

        @Override
        public void setPing(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getInitSql() {
            return null;
        }

        @Override
        public void setInitSql(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getDriverClassname() {
            if(getResType().equals(ConnectorConstants.JAVA_SQL_DRIVER)){
                return desc.getClassName();
            }
            return null;
        }

        @Override
        public void setDriverClassname(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getStatementLeakTimeoutInSeconds() {
            return String.valueOf(0);
        }

        @Override
        public void setStatementLeakTimeoutInSeconds(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getStatementLeakReclaim() {
            return String.valueOf(false);
        }

        @Override
        public void setStatementLeakReclaim(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getStatementCacheType() {
                return null;
        }

        @Override
        public void setStatementCacheType(String value) throws PropertyVetoException {
            //do nothing
        }

        @Override
        public String getDeploymentOrder() {
            return null;
        }

        @Override
        public void setDeploymentOrder(String value) {
            //do nothing
        }
    }
}
