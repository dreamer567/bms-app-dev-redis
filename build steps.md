to validate azure app service and azure database for mysql flexible server

az login

re-generate ssh key every morning before using azure devops pipeline




git clone git@github.com:dreamer567/bms-app-dev.git my-webapp
cd my-webapp/

optional:
mvn com.microsoft.azure:azure-webapp-maven-plugin:2.14.1:config
1:application
enter
enter
...
Y
optional end

mvn clean package

mvn azure-webapp:deploy

https://bms-dev-asp-001-w.azurewebsites.net/greeting

https://[app_service_name].azurewebsites.net/greeting

mysql -h bmssql.mysql.database.azure.com -P 3306 -u sqladmin -p

