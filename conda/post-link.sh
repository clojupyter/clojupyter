cat > ${PREFIX}/share/jupyter/kernels/${PKG_NAME}-${PKG_VERSION}/kernel.json << FIN
{"env": {"CLASSPATH": "${PREFIX}/Library/${PKG_NAME}-${PKG_VERSION}/${PKG_NAME}-${PKG_VERSION}.jar:${PREFIX}/Library/${PKG_NAME}-${PKG_VERSION}/plugins/enabled/*:\${CLASSPATH}"},
 "arvg": ["java", "clojupyter.kernel.core", "{connection_file}"],
 "display_name": "clojure",
 "interrupt_mode": "message"}
FIN
