# Set up the database - we need to set up a custom user otherwise we'll have trouble connecting in Travis CI
mysql -e "CREATE DATABASE IF NOT EXISTS stroom;"
mysql -e "CREATE USER 'stroomuser'@'localhost' IDENTIFIED BY 'stroompassword1';"
mysql -e "GRANT ALL PRIVILEGES ON * . * TO 'stroomuser'@'localhost';"
mysql -e "FLUSH PRIVILEGES"

# Install the urlDependencies plugin
git clone https://github.com/gchq/urlDependencies-plugin.git
cd urlDependencies-plugin
./gradlew clean build publishToMavenLocal
cd ..