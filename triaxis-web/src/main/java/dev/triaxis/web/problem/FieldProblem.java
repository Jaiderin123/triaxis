package dev.triaxis.web.problem;

/** One invalid field of a request, named with the external (JSON) name. */
public record FieldProblem(String campo, String mensaje) {
}
