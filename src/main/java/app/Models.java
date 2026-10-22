package app;

import java.util.List;
import java.util.Map;

public class Models {

    public record ValidationError(int line, int column, String message) {}

    public record StepResult(String status, List<ValidationError> errors) {}

    public record ValidationResponse(
            StepResult xmlWellFormed,
            StepResult xsdValid,
            StepResult xmlMatchesXsd,
            boolean allValid,
            Map<String, String> namespaces
    ) {}

    public record ValidationRequest(String xml, String xsd) {}

    public record XPathRequest(String xml, String expression) {}

    public record XPathResponse(
            String type,
            int count,
            boolean truncated,
            List<Map<String, Object>> items
    ) {}
}
