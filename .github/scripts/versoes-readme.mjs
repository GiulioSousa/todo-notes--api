#!/usr/bin/env node
//
// Mantem o bloco de versoes do README.md igual ao pom.xml.
//
// POR QUE ISTO EXISTE
//
// A versao DO REPOSITORIO quem escreve e o release-please, no bloco anotado do README. As
// versoes da PILHA - Java, Spring Boot, as duas bibliotecas fixadas a mao - nao: elas moram
// no pom.xml e sobem por PR do Dependabot, que nao sabe que ha um README falando delas. Sem
// este script, a tabela do README envelhece em silencio, e um README que mente sobre a
// versao do Spring Boot e pior do que um README sem tabela.
//
// COMO RODAR
//
//   node .github/scripts/versoes-readme.mjs             # escreve o bloco
//   node .github/scripts/versoes-readme.mjs --conferir   # so confere, e falha se divergir
//
// O `--conferir` e o que o CI roda: ele transforma a defasagem em falha de build enquanto o
// PR do Dependabot ainda esta aberto.

import { readFileSync, writeFileSync } from 'node:fs';

const INICIO = '<!-- versoes:inicio -->';
const FIM = '<!-- versoes:fim -->';

const conferir = process.argv.includes('--conferir');
const pom = readFileSync('pom.xml', 'utf8');
const readme = readFileSync('README.md', 'utf8');

/** O primeiro `<version>` dentro do `<parent>` - nao o do proprio projeto, que vem depois. */
function versaoDoParent() {
  const bloco = pom.match(/<parent>([\s\S]*?)<\/parent>/);
  const versao = bloco?.[1].match(/<version>([^<]+)<\/version>/);
  if (!versao) {
    throw new Error('nao achei a versao do parent no pom.xml');
  }
  return versao[1].trim();
}

function propriedade(nome) {
  const achado = pom.match(new RegExp(`<${nome}>([^<]+)</${nome}>`));
  if (!achado) {
    throw new Error(`nao achei a propriedade <${nome}> no pom.xml`);
  }
  return achado[1].trim();
}

const linhas = [
  ['Java', propriedade('java\\.version')],
  ['Spring Boot', versaoDoParent()],
  ['JJWT', propriedade('jjwt\\.version')],
  ['Bucket4j', propriedade('bucket4j\\.version')],
];

const tabela = [
  '| Peça | Versão |',
  '| --- | --- |',
  ...linhas.map(([peca, versao]) => `| ${peca} | ${versao} |`),
].join('\n');

const blocoNovo = `${INICIO}\n\n${tabela}\n\n${FIM}`;

const inicio = readme.indexOf(INICIO);
const fim = readme.indexOf(FIM);
if (inicio === -1 || fim === -1) {
  console.error(`ERRO: o README.md nao tem o par ${INICIO} ... ${FIM}`);
  process.exit(2);
}

const atualizado = readme.slice(0, inicio) + blocoNovo + readme.slice(fim + FIM.length);

if (atualizado === readme) {
  console.log('O bloco de versoes do README esta em dia.');
  process.exit(0);
}

if (conferir) {
  console.error('ERRO: o bloco de versoes do README nao corresponde ao pom.xml.');
  console.error('Rode: node .github/scripts/versoes-readme.mjs');
  console.error('');
  console.error('Esperado:');
  console.error(tabela);
  process.exit(1);
}

writeFileSync('README.md', atualizado);
console.log('Bloco de versoes do README atualizado.');
