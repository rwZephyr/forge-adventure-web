"""Audit preserved Stage B observations and dependencies; never executes game/browser/server."""
import hashlib
import json
import re
import shutil
import subprocess
import sys
from pathlib import Path

ROOT=Path(__file__).resolve().parents[3]
PROJECT=ROOT/'experiments/ai-teavm'
BUILD=PROJECT/'build'
EVIDENCE=PROJECT/'evidence'
sys.path.insert(0,str(ROOT/'experiments/rules-teavm/scripts'))
from collect_evidence import declared_licenses

def read(path):
    return json.loads(path.read_text(encoding='utf-8-sig'))

def write(name,value):
    (EVIDENCE/name).write_text(json.dumps(value,indent=2)+'\n',encoding='utf-8')

def sha(path):
    return hashlib.sha256(path.read_bytes()).hexdigest()

def main():
    original=subprocess.check_output(['git','diff','db893dcdb14fdd85a05437ad458dc70ef704ca2e','--',
        'forge-core','forge-game','forge-ai','pom.xml'],cwd=ROOT)
    assert not original.strip(), 'Original source/POM changed'
    unchanged=subprocess.check_output(['git','diff','cc3a5f91d29a4b05028ba88a9c1630beb83c6508','--',
        'experiments/rules-teavm','experiments/adventure-teavm'],cwd=ROOT)
    assert not unchanged.strip(), 'Successful experiment changed'
    inputs=read(BUILD/'input-manifest.json')
    for row in inputs['inputs']:
        assert sha(ROOT/row['path'])==row['sha256'],row['path']
    for row in read(EVIDENCE/'fixture-inputs.json'):
        assert sha(ROOT/row['path'])==row['sha256'],row['path']
        if row['path'].endswith('en-US.properties'):
            assert sha(BUILD/'fixture/languages/en-US.properties')==row['sha256']
        if row['path'].endswith('TypeLists.txt'):
            assert sha(BUILD/'fixture/TypeLists.txt')==row['sha256']
    checks=[]
    for name in ('positive-1','positive-2','positive-3','negative-1'):
        data=read(BUILD/'jvm'/f'{name}.json')
        assert data['passed'] and data['stage']=='complete' and data['survivingEvalThreads']==0
        assert data['libraryCounts']==[10,10] and data['manaAbilityCounts']==[1,1]
        if name.startswith('positive'):
            assert data['paidMana']=='R' and data['tappedMountains']==1 and data['payingManaAbilityCount']==1
            assert data['lifeAfter']==[20,0] and data['stackBeforeResolution']==1 and data['stackAfterResolution']==0
            assert data['gameOver'] and data['boltCandidateCount']==2 and data['targetPlayerId']==1
            assert data['effectClass']=='forge.game.ability.effects.DamageDealEffect'
            assert data['decisionMs']<2000
        else:
            assert data['emptyHand'] and data['decision'] is None and data['boltCandidateCount']==0
            assert data['manaCandidateCount']==2 and data['candidateCount']==2
        for suffix in ('json','log'):
            shutil.copyfile(BUILD/'jvm'/f'{name}.{suffix}',EVIDENCE/f'jvm-{name}.{suffix}')
        checks.append({'run':name,'verified':True,'decisionMs':data['decisionMs'],'elapsedMs':data['elapsedMs']})
    launch=read(BUILD/'jvm/runs.json')
    assert len(launch)==4 and all(r['completed'] and r['exitCode']==0 and r['elapsedMs']<15000 for r in launch)
    assert len({r['pid'] for r in launch})==4,'Fixtures did not use four distinct processes'
    write('jvm-launches.json',launch)
    for mode in ('positive','negative'):
        witness=read(BUILD/'traces'/f'{mode}-witness.json')
        fixture=read(BUILD/'traces'/f'{mode}-fixture.json')
        assert witness['transport']=='dt_shmem' and not witness['networkListener']
        assert witness['pathWitnessPassed'] and not witness['watchdogExpired'] and witness['childExit']==0 and fixture['passed']
        assert not witness['debuggerInvokedTargetMethods']
        events=witness['events']
        assert any(e['site']=='future-construction' and e['candidateListSize']>0 for e in events)
        caller={e['threadId'] for e in events if e['site']=='timed-get'}
        evaluator={e['threadId'] for e in events if e['site']=='evaluation-lambda' and e['thread']=='Game AI Eval'}
        assert caller and evaluator and caller.isdisjoint(evaluator)
        entries=[e for e in events if e['site']=='canPlayAndPayFor-entry']
        completed=[e for e in events if e['site']=='timed-get-finally' and e['futureState']==2]
        assert completed
        if mode=='positive':
            selected=[e for e in entries if e['hostCardName']=='Lightning Bolt' and e['thread']=='Game AI Eval']
            assert selected
            assert any(e['outcomeObjectId']==selected[0]['abilityObjectId'] and e['outcomeCardId']==fixture['chosenCardId'] for e in completed)
        else:
            assert entries and all(e['hostCardName']=='Mountain' for e in entries)
            assert all('outcomeObjectId' not in e for e in completed)
            assert not witness['emptyGuardObserved']
        for path in sorted((BUILD/'traces').glob(mode+'-*')):
            shutil.copyfile(path,EVIDENCE/('jdi-'+path.name))
        checks.append({'run':'jdi-'+mode,'verified':True,'transport':'dt_shmem','events':len(events)})
    metadata=read(BUILD/'metadata.json')
    policy=[s for s in (BUILD/'constructors.tsv').read_text().splitlines() if s and not s.startswith('#')]
    full=[s for s in (BUILD/'audited-constructors.tsv').read_text().splitlines() if s and not s.startswith('#')]
    assert len(full)==metadata['auditedConstructorSignatures']==491
    assert len(policy)==metadata['policyConstructorSignatures']==346
    assert metadata['triggerEnumEntries']==149 and metadata['apiEnumEntries']==203 and metadata['aiMappings']==193
    assert len(set(metadata['policyAiClasses']))==2
    for source,dest in [('metadata.json','metadata.json'),('constructors.tsv','reflection-constructors.tsv'),('audited-constructors.tsv','audited-constructors.tsv'),('dependencies.json','dependencies.json')]:
        shutil.copyfile(BUILD/source,EVIDENCE/dest)
    dependencies=read(BUILD/'dependencies.json')
    coordinates=sorted({r['coordinate'] for r in dependencies})
    licenses=[{'coordinate':c,**declared_licenses(c)} for c in coordinates]
    assert all(r['licenses'] for r in licenses),'Unresolved license declaration'
    write('dependency-licenses.json',licenses)
    status=read(EVIDENCE/'browserOriginal-status.json')
    log=(EVIDENCE/'browserOriginal.log').read_text(encoding='utf-8-sig')
    starts=list(re.finditer(r'^ERROR: .*$',log,re.MULTILINE))
    assert status['exitCode']!=0 and starts,'Expected actual TeaVM diagnostics missing'
    assert len(starts)==int(re.search(r'ORIGINAL_AI_COMPILER severeProblems=(\d+)',log).group(1))
    diagnostics=[]
    for index,start in enumerate(starts):
        end=starts[index+1].start() if index+1<len(starts) else log.find('ORIGINAL_AI_COMPILER severeProblems=',start.start())
        if end<0:end=len(log)
        diagnostics.append({'ordinal':index+1,'headline':start.group(0).strip(),'diagnostic':log[start.start():end].strip()})
    write('compiler-diagnostics.json',diagnostics)
    assert not (BUILD/'web/original/app.js').exists(),'Invalid/partial artifact retained'
    write('verification.json',{'checks':checks,'originalSourceAndPomsUnchanged':True,'successfulExperimentsUnchanged':True,
        'originalInputFilesVerified':len(inputs['inputs']),'originalInputManifestSha256':sha(BUILD/'input-manifest.json'),
        'auditedConstructors':491,'policyConstructors':346,'dependencyEntries':len(dependencies),'uniqueDependencies':len(coordinates),
        'allLicenseDeclarationsResolved':True,'compilerExit':status['exitCode'],'compilerErrorCount':len(diagnostics),
        'firstReportedDiagnostic':diagnostics[0],'runnableBrowserArtifact':False,'serversStarted':False,
        'negativeControlCorrection':'Empty hand retains original Mountain mana candidates and threaded evaluation; null choice/no payment/no damage verified. Zero-task assumption disproved.'})
    # Complete original input manifest remains reviewable; no tools/binaries are copied.
    shutil.copyfile(BUILD/'input-manifest.json',EVIDENCE/'input-manifest.json')
    rows=[]
    for path in sorted(EVIDENCE.iterdir()):
        if path.is_file() and path.name!='evidence-manifest.json':
            rows.append({'path':path.name,'bytes':path.stat().st_size,'sha256':sha(path)})
    write('evidence-manifest.json',rows)
    print('Verified JVM controls and original JDI path, constructor/dependency/license inventories, and full actual compiler diagnostics.')
    print('TeaVM errors:',len(diagnostics),'; first:',diagnostics[0]['headline'])

if __name__=='__main__':main()
