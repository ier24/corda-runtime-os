package net.corda.ledger.persistence.query.impl

import net.corda.ledger.persistence.query.VaultNamedQueryRegistry
import net.corda.ledger.persistence.query.impl.parsing.VaultNamedQueryParser
import net.corda.ledger.persistence.query.impl.parsing.VaultNamedQueryParserImpl
import net.corda.ledger.persistence.query.impl.parsing.converters.PostgresVaultNamedQueryConverter
import net.corda.ledger.persistence.query.impl.parsing.expressions.PostgresVaultNamedQueryExpressionParser
import net.corda.ledger.persistence.query.impl.parsing.expressions.VaultNamedQueryExpressionValidatorImpl
import net.corda.sandbox.type.UsedByPersistence
import net.corda.utilities.debug
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilder
import net.corda.v5.ledger.utxo.query.VaultNamedQueryBuilderFactory
import org.osgi.service.component.annotations.Activate
import org.osgi.service.component.annotations.Component
import org.osgi.service.component.annotations.Reference
import org.osgi.service.component.annotations.ReferenceScope
import org.osgi.service.component.annotations.ServiceScope
import org.slf4j.LoggerFactory

@Suppress("unused")
@Component(
    service = [
        VaultNamedQueryBuilderFactory::class,
        UsedByPersistence::class
    ],
    scope = ServiceScope.PROTOTYPE
)
class VaultNamedQueryBuilderFactoryImpl constructor(
    private val vaultNamedQueryRegistry: VaultNamedQueryRegistry,
    private val vaultNamedQueryParser: VaultNamedQueryParser
) : VaultNamedQueryBuilderFactory, UsedByPersistence {

    private companion object {
        private val logger = LoggerFactory.getLogger(this::class.java.enclosingClass)
    }

    @Activate
    constructor(
        @Reference(service = VaultNamedQueryRegistry::class, scope = ReferenceScope.PROTOTYPE)
        vaultNamedQueryRegistry: VaultNamedQueryRegistry
    ) : this(
        vaultNamedQueryRegistry,
        VaultNamedQueryParserImpl(
            PostgresVaultNamedQueryExpressionParser(),
            VaultNamedQueryExpressionValidatorImpl(),
            PostgresVaultNamedQueryConverter()
        )
    )

    override fun create(queryName: String): VaultNamedQueryBuilder {
        logger.debug { "Creating vault named query with name: $queryName" }
        return VaultNamedQueryBuilderImpl(vaultNamedQueryRegistry, vaultNamedQueryParser, queryName)
    }
}
