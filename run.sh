#!/bin/bash
# Script de ayuda para compilar y ejecutar agentesp2

if command -v mvn &> /dev/null; then
    MVN="mvn"
elif [ -x "/usr/lib/netbeans/java/maven/bin/mvn" ]; then
    MVN="/usr/lib/netbeans/java/maven/bin/mvn"
elif [ -x "/snap/netbeans/current/netbeans/java/maven/bin/mvn" ]; then
    MVN="/snap/netbeans/current/netbeans/java/maven/bin/mvn"
else
    MVN="mvn"
fi

cd "$(dirname "$0")"

ACTION="${1:-main}"

case "$ACTION" in
    compile)
        echo "🔨 Compilando proyecto..."
        $MVN compile
        ;;
    test)
        echo "🧪 Ejecutando TestConexion (comprobación de retos)..."
        $MVN compile exec:java -Dexec.mainClass="com.mycompany.agentesp2.TestConexion"
        ;;
    main)
        echo "🚀 Ejecutando Main (JADE + TermostatoAgent)..."
        $MVN compile exec:java
        ;;
    clean)
        echo "🧹 Limpiando proyecto..."
        $MVN clean
        ;;
    *)
        echo "Uso: ./run.sh [compile|test|main|clean]"
        exit 1
        ;;
esac
