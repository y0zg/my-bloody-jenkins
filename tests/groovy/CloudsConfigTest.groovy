import org.yaml.snakeyaml.Yaml

handler = 'Clouds'
configHandler = evaluate(new File("/usr/share/jenkins/config-handlers/${handler}Config.groovy"))

def assertCloud(id, type, closure){
    def cloud = jenkins.model.Jenkins.instance.clouds.find{it.name == id}
    assert type.isInstance(cloud) : "Cloud ${id} is not instanceof ${type}"
    if(closure){
        closure(cloud)
    }
}

def testEcs(){
	def config = new Yaml().load("""
ecs-cloud:
  type: ecs
  credentialsId: aws-cred
  region: us-east-1
  cluster: ecs-cluster
  connectTimeout: 60
  jenkinsUrl: http://127.0.0.1:8080
  tunnel: 127.0.0.1:8080
  templates:
    - name: ecs-template
      labels:
        - test
        - generic
      image: odavid/jenkins-jnlp-slave:latest
      remoteFs: /home/jenkins
      memory: 4000
      memoryReservation: 2000
      cpu: 512
      jvmArgs: -Xmx1G
      entrypoint: /entrypoint.sh
      logDriver: aws
      dns: 8.8.8.8
      privileged: true
      containerUser: aUser
      ports:
        - 9000:9001
      logDriverOptions:
        optionA: optionAValue
        optionB: optionBValue
      environment:
        ENV1: env1Value
        ENV2: env2Value
      extraHosts:
        extrHost1: extrHost1
        extrHost2: extrHost2
      volumes:
        - /home/xxx
        - /home/bbb:ro
        - /home/ccc:rw
        - /home/yyy:/home/yyy
        - /home/zzz:/home/zzz:ro
        - /home/aaa:/home/aaa:rw
        - /home/aaa1:/home/aaa1234:rw
  
""")
    configHandler.setup(config)

    assertCloud('ecs-cloud', com.cloudbees.jenkins.plugins.amazonecs.ECSCloud){
        assert it.credentialsId == 'aws-cred'
        assert it.regionName == 'us-east-1'
        assert it.cluster == 'ecs-cluster'
        assert it.slaveTimoutInSeconds == 60
        assert it.jenkinsUrl == 'http://127.0.0.1:8080'
        assert it.tunnel == '127.0.0.1:8080'
        def template = it.templates[0]
        assert template.templateName == 'ecs-template'
        assert template.label == 'test generic'
        assert template.image == 'odavid/jenkins-jnlp-slave:latest'
        assert template.remoteFSRoot == '/home/jenkins'
        assert template.memory == 4000
        assert template.memoryReservation == 2000
        assert template.cpu == 512
        assert template.jvmArgs == '-Xmx1G'
        assert template.entrypoint == '/entrypoint.sh'
        assert template.logDriver == 'aws'
        assert template.dnsSearchDomains == '8.8.8.8'
        assert template.privileged
        assert template.containerUser == 'aUser'
        assert ['optionA=optionAValue', 'optionB=optionBValue'] == template.logDriverOptions.collect{ 
            "${it.name}=${it.value}"
        }
        assert ['ENV1=env1Value', 'ENV2=env2Value'] == template.environments.collect{ 
            "${it.name}=${it.value}"
        }
        assert ['extrHost1=extrHost1', 'extrHost2=extrHost2'] == template.extraHosts.collect{ 
            "${it.ipAddress}=${it.hostname}"
        }
        assert template.portMappings[0].containerPort == 9001
        assert template.portMappings[0].hostPort == 9000
        assert template.portMappings[0].protocol == 'tcp'
        
        def mountPoints = template.mountPoints
        def assertMountPoint = { name, sourcePath, containerPath, readOnly ->
            def mpe = mountPoints.find{it.name == configHandler.pathToVolumeName(name)}
            assert mpe.sourcePath == sourcePath
            assert mpe.containerPath == containerPath
            assert mpe.readOnly == readOnly
        }
        assertMountPoint('/home/xxx', null, '/home/xxx', false)
        assertMountPoint('/home/bbb', null, '/home/bbb', true)
        assertMountPoint('/home/ccc', null, '/home/ccc', false)
        assertMountPoint('/home/yyy', '/home/yyy', '/home/yyy', false)
        assertMountPoint('/home/zzz', '/home/zzz', '/home/zzz', true)
        assertMountPoint('/home/aaa1', '/home/aaa1', '/home/aaa1234', false)
    }
}

def testKubernetes(){
	def config = new Yaml().load("""
kube-cloud:
  type: kubernetes
  namespace: jenkins-ns
  jenkinsUrl: http://127.0.0.1:8080
  serverUrl: http://127.0.0.1:6000
  tunnel: 127.0.0.1:8080
  credentialsId: kube-cred
  skipTlsVerify: true
  serverCertificate: kube-cred
  maxRequestsPerHost: 10
  connectTimeout: 10
  retentionTimeout: 10
  readTimeout: 10
  containerCap: 10
  defaultsProviderTemplate: defaultsProviderTemplate
  templates:
    - name: kube-cloud
      namespace: jenkins-ns
      inheritFrom: general-pod
      nodeSelector: nodeSelector
      serviceAccount: jenkins-service-account
      slaveConnectTimeout: 60
      instanceCap: 10
      imagePullSecrets:
        - xxx
        - yyy
      labels:
        - generic
        - kubernetes
      image: odavid/jenkins-jnlp-template:latest
      command: /run/me
      args: x y z
      remoteFs: /home/jenkins
      tty: true
      privileged: true
      alwaysPullImage: true
      environment:
        ENV1: env1Value
        ENV2: env2Value
      ports:
        - 9090:8080
        - 1500
      resourceRequestMemory: 1024Mi
      resourceRequestCpu: 512m
      resourceLimitCpu: 1024m
      resourceLimitMemory: 512Mi
      volumes:
        - /home/xxx
        - /home/bbb:ro
        - /home/ccc:rw
        - /home/yyy:/home/yyy
        - /home/zzz:/home/zzz:ro
        - /home/aaa:/home/aaa:rw
        - /home/aaa1:/home/aaa1234:rw
      livenessProbe:
        execArgs: cat /xxx
        timeoutSeconds: 10
        initialDelaySeconds: 10
        failureThreshold: 10
        periodSeconds: 10
        successThreshold: 10
""")
    configHandler.setup(config)

    assertCloud('kube-cloud', org.csanchez.jenkins.plugins.kubernetes.KubernetesCloud){
        assert it.credentialsId == 'kube-cred'
        assert it.namespace == 'jenkins-ns'
        assert it.serverUrl == 'http://127.0.0.1:6000'
        assert it.jenkinsUrl == 'http://127.0.0.1:8080'
        assert it.jenkinsTunnel == '127.0.0.1:8080'
        assert it.skipTlsVerify
        assert it.serverCertificate == 'kube-cred'
        assert it.maxRequestsPerHostStr == '10'
        assert it.connectTimeout == 10
        assert it.retentionTimeout == 10
        assert it.readTimeout == 10
        assert it.containerCap == 10
        assert it.defaultsProviderTemplate == 'defaultsProviderTemplate'

        def template = it.templates[0]

        assert template.name == 'kube-cloud'
        assert template.namespace == 'jenkins-ns'
        assert template.inheritFrom == 'general-pod'
        assert template.nodeSelector == 'nodeSelector'
        assert template.serviceAccount == 'jenkins-service-account'
        assert template.slaveConnectTimeout == 60
        assert template.instanceCapStr == '10'
        assert template.imagePullSecrets.collect{it.name} == ['xxx', 'yyy']
        assert template.label == 'generic kubernetes'
        assert template.image == 'odavid/jenkins-jnlp-template:latest'
        assert template.command == '/run/me'
        assert template.args == 'x y z'
        assert template.remoteFs == '/home/jenkins'
        
        assert template.containers[0].name == 'jnlp'
        assert template.containers[0].ttyEnabled
        assert template.containers[0].privileged
        assert template.containers[0].alwaysPullImage
        assert template.containers[0].envVars.collect{"${it.key}=${it.value}"} == ['ENV1=env1Value', 'ENV2=env2Value']
        assert template.containers[0].resourceRequestMemory == '1024Mi'
        assert template.containers[0].resourceRequestCpu == '512m'
        assert template.containers[0].resourceLimitCpu == '1024m'
        assert template.containers[0].resourceLimitMemory == '512Mi'

        def mountPoints = template.volumes
        def assertMountPoint = { idx, type, mountPath, hostPath=null ->
            def mpe = mountPoints[idx]
            assert type.isInstance(mpe)
            assert mpe.mountPath == mountPath
            if(hostPath) assert mpe.hostPath == hostPath
        }
        assertMountPoint(0, org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume, '/home/xxx')
        assertMountPoint(1, org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume, '/home/bbb')
        assertMountPoint(2, org.csanchez.jenkins.plugins.kubernetes.volumes.EmptyDirVolume, '/home/ccc')
        assertMountPoint(3, org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume, '/home/yyy', '/home/yyy')
        assertMountPoint(4, org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume, '/home/zzz', '/home/zzz')
        assertMountPoint(5, org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume, '/home/aaa', '/home/aaa')
        assertMountPoint(6, org.csanchez.jenkins.plugins.kubernetes.volumes.HostPathVolume, '/home/aaa1234', '/home/aaa1')
        assert template.containers[0].livenessProbe.execArgs == 'cat /xxx'
        assert template.containers[0].livenessProbe.timeoutSeconds == 10
        assert template.containers[0].livenessProbe.initialDelaySeconds == 10
        assert template.containers[0].livenessProbe.failureThreshold == 10
        assert template.containers[0].livenessProbe.periodSeconds == 10
        assert template.containers[0].livenessProbe.successThreshold == 10
    }
}

def testDocker(){
	def config = new Yaml().load("""
docker-cloud:
  type: docker
  dockerHostUri: unix:///var/run/docker.sock
  jenkinsUrl: http://127.0.0.1:8080
  credentialsId: docker-cert
  containerCap: 20
  connectTimeout: 10
  readTimeout: 20
  apiVersion: 1.24
  dockerHostname: localhost
  exposeDockerHost: false
  templates:
    - image: odavid/jenkins-jnlp-slave:latest
      jnlpUser: jenkins
      pullCredentialsId: pull-cred-id
      dns:
        - 8.8.8.8
        - 8.8.7.7
      network: host
      command: /bin/bash
      volumes:
        - /home/xxx
        - /home/bbb:ro
        - /home/ccc:rw
        - /home/yyy:/home/yyy
        - /home/zzz:/home/zzz:ro
        - /home/aaa:/home/aaa:rw
        - /home/aaa1:/home/aaa1234:rw
      volumesFrom:
        - vvv1
        - vvv2
      environment:
        ENV1: env1Value
        ENV2: env2Value
      hostname: docker-host-name
      memory: 50
      memorySwap: 10
      cpu: 1024
      ports:
        - 9090:8080
        - 1500
      bindAllPorts: true
      privileged: true
      tty: true
      macAddress: mac-address              
""")
    configHandler.setup(config)

    assertCloud('docker-cloud', com.nirima.jenkins.plugins.docker.DockerCloud){
        assert it.containerCap == 20
        assert !it.exposeDockerHost
        assert it.dockerApi.dockerHost.uri == 'unix:///var/run/docker.sock'
        assert it.dockerApi.dockerHost.credentialsId == 'docker-cert'
        assert it.dockerApi.connectTimeout == 10
        assert it.dockerApi.apiVersion == '1.24'
        assert it.dockerApi.hostname == 'localhost'
        def template = it.templates[0]
        assert template.image == 'odavid/jenkins-jnlp-slave:latest'
        assert template.connector.jenkinsUrl == 'http://127.0.0.1:8080'
        assert template.connector.user == 'jenkins'
        assert template.mode == hudson.model.Node.Mode.EXCLUSIVE
        assert template.dockerTemplateBase.pullCredentialsId == 'pull-cred-id'
        assert template.dockerTemplateBase.dnsString == '8.8.8.8 8.8.7.7'
        assert template.dockerTemplateBase.network == 'host'
        assert template.dockerTemplateBase.dockerCommand == '/bin/bash'
        assert template.dockerTemplateBase.volumes == config['docker-cloud']['templates'][0].volumes
        assert template.dockerTemplateBase.volumesFrom2 == config['docker-cloud']['templates'][0].volumesFrom
        assert template.dockerTemplateBase.environmentsString == ['ENV1=env1Value', 'ENV2=env2Value'].join('\n')
        assert template.dockerTemplateBase.hostname == 'docker-host-name'
        assert template.dockerTemplateBase.memoryLimit == 50
        assert template.dockerTemplateBase.memorySwap == 10
        assert template.dockerTemplateBase.cpuShares == 1024
        assert template.dockerTemplateBase.bindPorts == '9090:8080 1500'
        assert template.dockerTemplateBase.bindAllPorts
        assert template.dockerTemplateBase.privileged
        assert template.dockerTemplateBase.tty
        assert template.dockerTemplateBase.macAddress == 'mac-address'
    }
}

def testClouds(){
    testEcs()
    testKubernetes()
    testDocker()
}

testClouds()