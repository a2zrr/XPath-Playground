package app;

import app.Models.ValidationRequest;
import app.Models.ValidationResponse;
import app.Models.XPathRequest;
import app.Models.XPathResponse;
import io.javalin.Javalin;
import io.javalin.http.staticfiles.Location;

import java.util.Map;

public class Main {

    public static void main(String[] args) {
        Javalin app = Javalin.create(config -> {
            config.staticFiles.add("/public", Location.CLASSPATH);
        });

        app.post("/api/validate", ctx -> {
            ValidationRequest req;
            try {
                req = ctx.bodyAsClass(ValidationRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "Malformed request body"));
                return;
            }

            if (req == null || req.xml() == null || req.xsd() == null) {
                ctx.status(400).json(Map.of("error", "Malformed request: 'xml' and 'xsd' fields required"));
                return;
            }

            ValidationResponse resp = ValidationService.validate(req.xml(), req.xsd());
            ctx.json(resp);
        });

        app.post("/api/xpath", ctx -> {
            XPathRequest req;
            try {
                req = ctx.bodyAsClass(XPathRequest.class);
            } catch (Exception e) {
                ctx.status(400).json(Map.of("error", "Malformed request body"));
                return;
            }

            if (req == null || req.xml() == null || req.expression() == null) {
                ctx.status(400).json(Map.of("error", "Missing xml or expression"));
                return;
            }

            try {
                XPathResponse resp = XPathService.evaluate(req.xml(), req.expression());
                ctx.json(resp);
            } catch (Exception e) {
                String msg = e.getMessage() != null ? e.getMessage() : e.toString();
                ctx.status(200).json(Map.of("error", msg));
            }
        });

        app.start("127.0.0.1", 7070);
    }
}
